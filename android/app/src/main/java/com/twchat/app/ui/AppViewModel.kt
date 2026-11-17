package com.twchat.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.twchat.app.data.ChatDatabase
import com.twchat.app.data.ChatRepository
import com.twchat.app.data.Message
import com.twchat.app.data.Person
import com.twchat.app.data.Session
import com.twchat.app.data.SessionStore
import com.twchat.app.data.remote.AuthResult
import com.twchat.app.data.remote.RealtimeClient
import com.twchat.app.data.remote.SocketEvent
import com.twchat.app.data.remote.TwoChatApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class Screen { Chat, Settings }

/** Volatile connection state: presence, typing bubble, socket health. */
data class LiveState(
    val connected: Boolean = false,
    val partnerOnline: Boolean = false,
    val partnerLastSeen: Long = 0L,
    val typingUntil: Long = 0L,
) {
    fun isTyping(now: Long = System.currentTimeMillis()): Boolean = typingUntil > now
}

/** One-shot UI signals (snackbars, spinners) and the current sub-screen. */
data class UiExtras(
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val screen: Screen = Screen.Chat,
)

data class AppUiState(
    val session: Session = Session(),
    val messages: List<Message> = emptyList(),
    val live: LiveState = LiveState(),
    val extras: UiExtras = UiExtras(),
) {
    val me: Person? get() = session.me
    val partner: Person? get() = session.partner
    val isLoggedIn: Boolean get() = session.isLoggedIn
    val isPaired: Boolean get() = session.partner != null
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(ChatDatabase.getInstance(application).messageDao())
    private val sessionStore = SessionStore(application)
    private val api = TwoChatApi()
    private val realtime = RealtimeClient(api)

    private val live = MutableStateFlow(LiveState())
    private val extras = MutableStateFlow(UiExtras())

    private val session = sessionStore.session
        .stateIn(viewModelScope, SharingStarted.Eagerly, Session())

    private val messages = repository.observeMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var startedToken: String? = null

    val uiState: StateFlow<AppUiState> =
        combine(session, messages, live, extras) { session, messages, live, extras ->
            AppUiState(session = session, messages = messages, live = live, extras = extras)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    init {
        viewModelScope.launch {
            session.collect { current ->
                api.baseUrl = current.serverUrl
                api.token = current.token
                if (current.token == null) {
                    if (startedToken != null) {
                        startedToken = null
                        realtime.stop()
                    }
                } else if (current.token != startedToken) {
                    startedToken = current.token
                    realtime.start()
                    refreshConversation()
                }
            }
        }
        viewModelScope.launch {
            realtime.events.collect { event -> handleSocketEvent(event) }
        }
    }

    // ------------------------------------------------------------------- auth

    fun login(serverUrl: String, username: String, password: String) = guarded {
        val url = normalizeServerUrl(serverUrl)
        require(username.isNotBlank()) { "Nhập tên đăng nhập" }
        require(password.isNotEmpty()) { "Nhập mật khẩu" }
        prepareConnection(url)
        val result = api.login(username.trim().lowercase(), password)
        completeAuth(url, result)
    }

    fun register(serverUrl: String, username: String, password: String, displayName: String) = guarded {
        val url = normalizeServerUrl(serverUrl)
        require(username.isNotBlank()) { "Nhập tên đăng nhập" }
        require(password.length >= 6) { "Mật khẩu cần ít nhất 6 ký tự" }
        prepareConnection(url)
        val result = api.register(username.trim().lowercase(), password, displayName.trim())
        completeAuth(url, result)
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { api.logout() }
            realtime.stop()
            startedToken = null
            repository.clear()
            sessionStore.clearAuth()
            live.value = LiveState()
            extras.update { UiExtras() }
        }
    }

    private suspend fun completeAuth(serverUrl: String, result: AuthResult) {
        api.token = result.token
        repository.clear()
        sessionStore.saveAuth(serverUrl, result.token, result.me, result.partner)
        if (result.partner != null) refreshConversation()
    }

    private fun prepareConnection(url: String) {
        api.baseUrl = url
        api.token = null
    }

    // ---------------------------------------------------------------- pairing

    fun pair(code: String) = guarded {
        val trimmed = code.trim().uppercase()
        require(trimmed.length == 6) { "Mã mời gồm 6 ký tự" }
        val partner = api.pair(trimmed)
        repository.clear()
        sessionStore.savePartner(partner)
        refreshConversation()
        extras.update { it.copy(notice = "Đã ghép đôi 🎉") }
    }

    fun unpair() = guarded {
        api.unpair()
        repository.clear()
        sessionStore.savePartner(null)
        live.update { it.copy(partnerOnline = false, typingUntil = 0) }
        extras.update { it.copy(screen = Screen.Chat, notice = "Đã huỷ ghép đôi") }
    }

    // --------------------------------------------------------------- composing

    fun send(text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        val me = session.value.me ?: return
        if (session.value.partner == null) return
        val clientId = UUID.randomUUID().toString()
        viewModelScope.launch {
            repository.addPending(clientId, body, me.id)
            deliver(clientId, body)
        }
    }

    fun retry(message: Message) {
        viewModelScope.launch {
            repository.markPending(message.clientId)
            deliver(message.clientId, message.body)
        }
    }

    private suspend fun deliver(clientId: String, body: String) {
        try {
            val remote = api.sendMessage(body, clientId)
            repository.confirmSent(clientId, remote)
        } catch (error: Throwable) {
            repository.markFailed(clientId)
            extras.update { it.copy(error = error.message ?: "Không gửi được tin nhắn") }
        }
    }

    fun sendTyping() {
        if (realtime.isConnected) {
            realtime.sendTyping()
        } else {
            viewModelScope.launch { runCatching { api.sendTyping() } }
        }
    }

    fun markRead() {
        val me = session.value.me ?: return
        val last = messages.value.lastOrNull { it.senderId != me.id && it.serverId != null } ?: return
        val serverId = last.serverId ?: return
        if (realtime.isConnected) {
            realtime.sendRead(serverId)
        } else {
            viewModelScope.launch { runCatching { api.markRead(serverId) } }
        }
    }

    // ---------------------------------------------------------------- profile

    fun updateProfile(displayName: String, color: Long) = guarded {
        val updated = api.updateProfile(displayName.trim().ifBlank { null }, color)
        if (updated != null) sessionStore.saveMe(updated)
        extras.update { it.copy(notice = "Đã lưu hồ sơ") }
    }

    // ------------------------------------------------------------ navigation

    fun openSettings() {
        extras.update { it.copy(screen = Screen.Settings) }
    }

    fun closeSettings() {
        extras.update { it.copy(screen = Screen.Chat) }
    }

    fun dismissError() {
        extras.update { it.copy(error = null) }
    }

    fun dismissNotice() {
        extras.update { it.copy(notice = null) }
    }

    // ------------------------------------------------------------ realtime

    private suspend fun handleSocketEvent(event: SocketEvent) {
        val me = session.value.me
        val partnerId = session.value.partner?.id
        when (event) {
            SocketEvent.Connected -> live.update { it.copy(connected = true) }
            SocketEvent.Disconnected -> live.update { it.copy(connected = false) }
            SocketEvent.Unauthorized -> handleUnauthorized()

            is SocketEvent.Hello -> {
                repository.storeAll(event.messages)
                sessionStore.saveMe(event.me)
                sessionStore.savePartner(event.partner)
                live.update {
                    it.copy(
                        connected = true,
                        partnerOnline = event.partner?.online ?: false,
                        partnerLastSeen = event.partner?.lastSeen ?: 0L,
                    )
                }
            }

            is SocketEvent.Incoming -> {
                repository.store(event.message)
                if (me != null && event.message.senderId != me.id && event.message.id > 0) {
                    if (realtime.isConnected) realtime.sendRead(event.message.id)
                    else runCatching { api.markRead(event.message.id) }
                }
            }

            is SocketEvent.Read -> if (me != null && event.userId == partnerId) {
                repository.applyReadReceipt(me.id, event.lastMessageId, event.readAt)
            }

            is SocketEvent.Typing -> if (event.userId == partnerId) {
                live.update { it.copy(typingUntil = System.currentTimeMillis() + 3_000) }
            }

            is SocketEvent.Presence -> if (event.userId == partnerId) {
                live.update { it.copy(partnerOnline = event.online, partnerLastSeen = event.lastSeen) }
            }

            is SocketEvent.Paired -> {
                repository.clear()
                sessionStore.savePartner(event.partner)
                refreshConversation()
                extras.update { it.copy(notice = "Đã ghép đôi 🎉") }
            }

            SocketEvent.Unpaired -> {
                repository.clear()
                sessionStore.savePartner(null)
                live.update { it.copy(partnerOnline = false, typingUntil = 0) }
                extras.update { it.copy(notice = "Đã huỷ ghép đôi") }
            }

            is SocketEvent.ProfileUpdated -> if (event.user?.id == partnerId) {
                sessionStore.savePartner(event.user)
            }
        }
    }

    private suspend fun handleUnauthorized() {
        realtime.stop()
        startedToken = null
        repository.clear()
        sessionStore.clearAuth()
        live.value = LiveState()
        extras.update {
            it.copy(error = "Phiên đăng nhập đã hết hạn, đăng nhập lại nhé", screen = Screen.Chat)
        }
    }

    private suspend fun refreshConversation() {
        runCatching {
            val snapshot = api.conversation()
            repository.storeAll(snapshot.messages)
            sessionStore.saveMe(snapshot.me)
            sessionStore.savePartner(snapshot.partner)
        }
    }

    // ---------------------------------------------------------------- helpers

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

    private fun normalizeServerUrl(raw: String): String {
        var url = raw.trim()
        require(url.isNotEmpty()) { "Nhập địa chỉ máy chủ" }
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        return url.trimEnd('/')
    }
}
