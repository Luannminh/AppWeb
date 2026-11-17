package com.twchat.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.twchat.app.data.AppSession
import com.twchat.app.data.ChatDatabase
import com.twchat.app.data.ChatRepository
import com.twchat.app.data.Message
import com.twchat.app.data.Person
import com.twchat.app.data.SessionStore
import com.twchat.app.data.supabase.AuthResult
import com.twchat.app.data.supabase.RealtimeEvent
import com.twchat.app.data.supabase.SupabaseGateway
import com.twchat.app.data.supabase.TokenSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class Screen { Chat, Settings }

/** Live connection state: socket health, typing bubble, presence. */
data class LiveState(
    val connected: Boolean = false,
    val typingUntil: Long = 0L,
    /** Last presence heartbeat received from the partner. */
    val lastPresenceAt: Long = 0L,
    val partnerLastSeen: Long = 0L,
) {
    fun isTyping(now: Long = System.currentTimeMillis()): Boolean = typingUntil > now

    fun partnerOnline(now: Long = System.currentTimeMillis()): Boolean =
        lastPresenceAt > 0 && now - lastPresenceAt < 45_000
}

/** One-shot UI signals (spinners, snackbars) plus the current sub-screen. */
data class UiExtras(
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val screen: Screen = Screen.Chat,
)

data class AppUiState(
    val session: AppSession = AppSession(),
    val messages: List<Message> = emptyList(),
    val live: LiveState = LiveState(),
    val extras: UiExtras = UiExtras(),
) {
    val me: Person? get() = session.me
    val partner: Person? get() = session.partner
    val isConfigured: Boolean get() = session.isConfigured
    val isLoggedIn: Boolean get() = session.isLoggedIn
    val isPaired: Boolean get() = session.partner != null
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(ChatDatabase.getInstance(application).messageDao())
    private val sessionStore = SessionStore(application)
    private val gateway = SupabaseGateway(sessionStore)

    private val live = MutableStateFlow(LiveState())
    private val extras = MutableStateFlow(UiExtras())

    private val session: StateFlow<AppSession> =
        sessionStore.session.stateIn(viewModelScope, SharingStarted.Eagerly, AppSession())

    private val messages = repository.observeMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var syncedUser: String? = null
    private var startedConversation: String? = null
    private var startedPartnerId: String? = null
    private var pollJob: Job? = null

    val uiState: StateFlow<AppUiState> =
        combine(session, messages, live, extras) { session, messages, live, extras ->
            AppUiState(session = session, messages = messages, live = live, extras = extras)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    init {
        viewModelScope.launch {
            session.collect { current -> onSessionChanged(current) }
        }
        viewModelScope.launch {
            gateway.events.collect { event -> handleRealtime(event) }
        }
    }

    // ------------------------------------------------------------------ auth

    fun signIn(serverUrl: String, anonKey: String, email: String, password: String) = guarded {
        require(email.contains("@")) { "Email không hợp lệ" }
        require(password.isNotEmpty()) { "Nhập mật khẩu" }
        val (url, key) = validateConfig(serverUrl, anonKey)
        applyConfig(url, key)
        persistAuth(gateway.signInWithEmail(email.trim(), password))
    }

    fun signUp(
        serverUrl: String,
        anonKey: String,
        email: String,
        password: String,
        displayName: String,
    ) = guarded {
        require(email.contains("@")) { "Email không hợp lệ" }
        require(password.length >= 6) { "Mật khẩu cần ít nhất 6 ký tự" }
        val (url, key) = validateConfig(serverUrl, anonKey)
        applyConfig(url, key)
        when (val result = gateway.signUpWithEmail(email.trim(), password, displayName.trim())) {
            is AuthResult.LoggedIn -> persistAuth(result.tokens)
            AuthResult.ConfirmEmail -> extras.update {
                it.copy(notice = "Đã tạo tài khoản. Mở email xác nhận rồi đăng nhập nhé.")
            }
        }
    }

    fun signInAnonymously(serverUrl: String, anonKey: String, displayName: String) = guarded {
        require(displayName.isNotBlank()) { "Nhập tên hiển thị" }
        val (url, key) = validateConfig(serverUrl, anonKey)
        applyConfig(url, key)
        persistAuth(gateway.signInAnonymously(displayName.trim()))
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { gateway.signOut() }
            gateway.stop()
            repository.clear()
            sessionStore.clearAuth()
            live.value = LiveState()
            extras.update { UiExtras() }
        }
    }

    // ---------------------------------------------------------------- pairing

    fun pair(code: String) = guarded {
        val trimmed = code.trim().uppercase()
        require(trimmed.length == 6) { "Mã mời gồm 6 ký tự" }
        gateway.pairWithCode(trimmed)
        repository.clear()
        syncConversation()
        extras.update { it.copy(notice = "Đã ghép đôi 🎉") }
    }

    fun unpair() = guarded {
        gateway.unpair()
        gateway.stopRealtime()
        startedConversation = null
        startedPartnerId = null
        repository.clear()
        sessionStore.savePartner(null)
        live.update { it.copy(typingUntil = 0, lastPresenceAt = 0) }
        syncConversation()
        extras.update { it.copy(screen = Screen.Chat, notice = "Đã huỷ ghép đôi") }
    }

    // -------------------------------------------------------------- composing

    fun send(text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        val current = session.value
        val me = current.me ?: return
        val conversationId = me.conversationId
        if (conversationId == null) {
            extras.update { it.copy(error = "Hãy ghép đôi trước khi nhắn tin") }
            return
        }
        val clientId = UUID.randomUUID().toString()
        viewModelScope.launch {
            repository.addPending(clientId, body, me.id)
            deliver(clientId, conversationId, me.id, body)
        }
    }

    fun retry(message: Message) {
        val me = session.value.me ?: return
        val conversationId = me.conversationId ?: return
        viewModelScope.launch {
            repository.markPending(message.clientId)
            deliver(message.clientId, conversationId, me.id, message.body)
        }
    }

    private suspend fun deliver(
        clientId: String,
        conversationId: String,
        myId: String,
        body: String,
    ) {
        try {
            val remote = gateway.sendMessage(clientId, conversationId, myId, body)
            repository.confirmSent(clientId, remote)
            live.update { it.copy(connected = true) }
        } catch (error: Throwable) {
            repository.markFailed(clientId)
            extras.update { it.copy(error = error.message ?: "Không gửi được tin nhắn") }
        }
    }

    fun sendTyping() = gateway.sendTyping()

    fun markRead() {
        val current = session.value
        val me = current.me ?: return
        val conversationId = me.conversationId ?: return
        val last = messages.value.lastOrNull { it.senderId != me.id && it.serverId != null } ?: return
        val serverId = last.serverId ?: return
        viewModelScope.launch { runCatching { gateway.markRead(conversationId, me.id, serverId) } }
    }

    // ---------------------------------------------------------------- profile

    fun updateProfile(displayName: String, color: Long) = guarded {
        val me = session.value.me ?: return@guarded
        gateway.updateProfile(me.id, displayName.trim(), color)
        syncConversation()
        extras.update { it.copy(notice = "Đã lưu hồ sơ") }
    }

    // ------------------------------------------------------------ navigation

    fun openSettings() = extras.update { it.copy(screen = Screen.Settings) }

    fun closeSettings() = extras.update { it.copy(screen = Screen.Chat) }

    fun dismissError() = extras.update { it.copy(error = null) }

    fun dismissNotice() = extras.update { it.copy(notice = null) }

    // -------------------------------------------------------------- realtime

    private suspend fun handleRealtime(event: RealtimeEvent) {
        val me = session.value.me
        val partnerId = session.value.partner?.id
        when (event) {
            RealtimeEvent.Connected -> live.update { it.copy(connected = true) }
            RealtimeEvent.Disconnected -> live.update { it.copy(connected = false) }

            is RealtimeEvent.MessageInserted -> {
                repository.store(event.message)
                if (me != null && event.message.senderId != me.id) {
                    live.update { it.copy(lastPresenceAt = System.currentTimeMillis()) }
                    me.conversationId?.let { conversationId ->
                        runCatching { gateway.markRead(conversationId, me.id, event.message.id) }
                    }
                }
            }

            is RealtimeEvent.MessageUpdated -> repository.store(event.message)

            is RealtimeEvent.PartnerUpdated -> {
                sessionStore.savePartner(event.person)
                live.update { it.copy(partnerLastSeen = event.person.lastSeen) }
            }

            is RealtimeEvent.Typing -> if (event.from == partnerId) {
                live.update { it.copy(typingUntil = System.currentTimeMillis() + 3_000) }
            }

            is RealtimeEvent.Presence -> if (event.from == partnerId) {
                live.update { it.copy(lastPresenceAt = System.currentTimeMillis()) }
            }
        }
    }

    // ----------------------------------------------------------------- wiring

    private suspend fun onSessionChanged(current: AppSession) {
        if (!current.isConfigured) return
        gateway.configure(current.serverUrl, current.anonKey)

        val tokenSet = current.accessToken?.let { access ->
            TokenSet(
                accessToken = access,
                refreshToken = current.refreshToken.orEmpty(),
                expiresAt = current.expiresAt,
                userId = current.userId.orEmpty(),
                email = current.email,
                isAnonymous = current.isAnonymous,
            )
        }
        gateway.applyTokens(tokenSet)

        if (!current.isLoggedIn) {
            gateway.stopRealtime()
            pollJob?.cancel()
            pollJob = null
            syncedUser = null
            startedConversation = null
            startedPartnerId = null
            return
        }

        val userId = current.userId ?: return
        if (syncedUser != userId) {
            syncedUser = userId
            syncConversation()
            startPolling()
        }

        val conversationId = current.me?.conversationId
        val partnerId = current.partner?.id
        if (conversationId == null) {
            if (startedConversation != null) {
                gateway.stopRealtime()
                startedConversation = null
                startedPartnerId = null
            }
            return
        }
        if (conversationId != startedConversation || partnerId != startedPartnerId) {
            startedConversation = conversationId
            startedPartnerId = partnerId
            gateway.startRealtime(conversationId, userId, partnerId)
        }
    }

    private suspend fun syncConversation() {
        val userId = session.value.userId ?: return
        try {
            val snapshot = gateway.loadConversation(userId)
            repository.storeAll(snapshot.messages)
            sessionStore.saveMe(snapshot.me)
            sessionStore.savePartner(snapshot.partner)
            gateway.touchLastSeen(userId)
        } catch (error: Throwable) {
            extras.update { it.copy(error = error.message ?: "Không tải được cuộc trò chuyện") }
        }
    }

    /** Safety net: keeps messages fresh when the socket cannot connect. */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                delay(5_000)
                val current = session.value
                if (current.userId == null) continue
                val conversationId = current.me?.conversationId ?: continue
                tick += 1
                // Every minute: heartbeat + re-read my profile, so an unpair done on
                // the other phone is noticed even when realtime is healthy.
                if (tick % 12 == 0) syncConversation()
                if (gateway.isRealtimeConnected) continue
                runCatching { gateway.refreshMessages(conversationId) }
                    .onSuccess { repository.storeAll(it) }
            }
        }
    }

    private suspend fun persistAuth(tokens: TokenSet) {
        repository.clear()
        gateway.applyTokens(tokens)
        sessionStore.saveAuth(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresAt = tokens.expiresAt,
            userId = tokens.userId,
            email = tokens.email,
            isAnonymous = tokens.isAnonymous,
        )
    }

    private suspend fun applyConfig(serverUrl: String, anonKey: String) {
        gateway.configure(serverUrl, anonKey)
        sessionStore.saveConfig(serverUrl, anonKey)
    }

    private fun validateConfig(serverUrl: String, anonKey: String): Pair<String, String> {
        val url = serverUrl.trim().trimEnd('/')
        require(url.isNotBlank()) { "Nhập Project URL của Supabase" }
        require(url.startsWith("https://") || url.startsWith("http://")) {
            "URL cần bắt đầu bằng https:// (ví dụ https://abcd.supabase.co)"
        }
        val key = anonKey.trim()
        require(key.length > 20) { "Nhập anon public key (Project Settings → API)" }
        return url to key
    }

    private fun guarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            extras.update { it.copy(loading = true, error = null) }
            try {
                block()
            } catch (error: Throwable) {
                extras.update { it.copy(error = error.message ?: "Có lỗi xảy ra") }
            } finally {
                extras.update { it.copy(loading = false) }
            }
        }
    }
}
