package com.twchat.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.twchat.app.data.AppSession
import com.twchat.app.data.ChatDatabase
import com.twchat.app.data.ChatRepository
import com.twchat.app.data.Message
import com.twchat.app.data.MessageKind
import com.twchat.app.data.MessageStatus
import com.twchat.app.data.Person
import com.twchat.app.data.SessionStore
import com.twchat.app.data.supabase.Attachment
import com.twchat.app.data.supabase.AuthResult
import com.twchat.app.data.supabase.OutgoingMessage
import com.twchat.app.data.supabase.RealtimeEvent
import com.twchat.app.data.supabase.SupabaseGateway
import com.twchat.app.data.supabase.TokenSet
import com.twchat.app.util.Notify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
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
        // Broadcast presence when realtime works, refreshed last_seen otherwise.
        (lastPresenceAt > 0 && now - lastPresenceAt < 45_000) ||
            (partnerLastSeen > 0 && now - partnerLastSeen < 90_000)
}

/** One-shot UI signals (spinners, snackbars) plus the current sub-screen. */
data class UiExtras(
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val screen: Screen = Screen.Chat,
    val uploading: Boolean = false,
    val searchQuery: String = "",
    val editing: Message? = null,
    val playingClientId: String? = null,
    val playProgress: Float = 0f,
)

data class AppUiState(
    val session: AppSession = AppSession(),
    val messages: List<Message> = emptyList(),
    val live: LiveState = LiveState(),
    val extras: UiExtras = UiExtras(),
    val searchResults: List<Message> = emptyList(),
) {
    val me: Person? get() = session.me
    val partner: Person? get() = session.partner
    val isConfigured: Boolean get() = session.isConfigured
    val isLoggedIn: Boolean get() = session.isLoggedIn
    val isPaired: Boolean get() = session.partner != null
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(ChatDatabase.getInstance(application).messageDao())
    private val sessionStore = SessionStore(application)
    private val gateway = SupabaseGateway(sessionStore)

    private val live = MutableStateFlow(LiveState())
    private val extras = MutableStateFlow(UiExtras())
    private val searchQuery = MutableStateFlow("")

    private val session: StateFlow<AppSession> =
        sessionStore.session.stateIn(viewModelScope, SharingStarted.Eagerly, AppSession())

    private val messages = repository.observeMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val searchResults = searchQuery
        .debounce(150)
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList()) else repository.search(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var syncedUser: String? = null
    private var startedConversation: String? = null
    private var startedPartnerId: String? = null
    private var pollJob: Job? = null

    private var foreground = false
    private var unread = 0

    private val imageCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private var player: MediaPlayer? = null
    private var playerJob: Job? = null

    val uiState: StateFlow<AppUiState> =
        combine(session, messages, live, extras, searchResults) { session, messages, live, extras, search ->
            AppUiState(
                session = session,
                messages = messages,
                live = live,
                extras = extras,
                searchResults = search,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    init {
        viewModelScope.launch {
            session.collect { current -> onSessionChanged(current) }
        }
        viewModelScope.launch {
            gateway.events.collect { event -> handleRealtime(event) }
        }
        Notify.ensureChannel(application)
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { player?.release() }
        player = null
        gateway.stop()
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
            stopVoice()
            runCatching { gateway.signOut() }
            gateway.stop()
            repository.clear()
            sessionStore.clearAuth()
            live.value = LiveState()
            extras.value = UiExtras()
            searchQuery.value = ""
            unread = 0
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
        stopVoice()
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
        val outgoing = OutgoingMessage(
            clientId = clientId,
            conversationId = conversationId,
            senderId = me.id,
            body = body,
        )
        val pending = Message(
            clientId = clientId,
            senderId = me.id,
            body = body,
            sentAt = System.currentTimeMillis(),
            status = MessageStatus.PENDING,
        )
        viewModelScope.launch { deliver(outgoing, pending) }
    }

    fun sendSticker(sticker: String) {
        val current = session.value
        val me = current.me ?: return
        val conversationId = me.conversationId ?: return
        val clientId = UUID.randomUUID().toString()
        val outgoing = OutgoingMessage(
            clientId = clientId,
            conversationId = conversationId,
            senderId = me.id,
            body = sticker,
            kind = MessageKind.STICKER,
            sticker = sticker,
        )
        val pending = Message(
            clientId = clientId,
            senderId = me.id,
            body = sticker,
            sentAt = System.currentTimeMillis(),
            status = MessageStatus.PENDING,
            kind = MessageKind.STICKER,
            sticker = sticker,
        )
        viewModelScope.launch { deliver(outgoing, pending) }
    }

    fun sendImage(uri: Uri) = sendUriAttachment(uri, MessageKind.IMAGE)

    fun sendFile(uri: Uri) = sendUriAttachment(uri, MessageKind.FILE)

    /** Called by the recorder once the user stops recording. */
    fun sendVoice(file: File, durationMs: Int) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull() }
            if (bytes == null || bytes.isEmpty()) {
                extras.update { it.copy(error = "Không đọc được bản ghi âm") }
                return@launch
            }
            uploadAndSend(
                bytes = bytes,
                mime = "audio/mp4",
                name = "voice-${System.currentTimeMillis()}.m4a",
                kind = MessageKind.VOICE,
                durationMs = durationMs,
            )
            withContext(Dispatchers.IO) { runCatching { file.delete() } }
        }
    }

    private fun sendUriAttachment(uri: Uri, kind: String) {
        viewModelScope.launch {
            try {
                extras.update { it.copy(uploading = true, error = null) }
                val payload = withContext(Dispatchers.IO) { readUri(uri, compress = kind == MessageKind.IMAGE) }
                uploadAndSend(
                    bytes = payload.bytes,
                    mime = payload.mime,
                    name = payload.name,
                    kind = kind,
                    durationMs = null,
                )
            } catch (error: Throwable) {
                extras.update { it.copy(error = error.message ?: "Không gửi được tệp") }
            } finally {
                extras.update { it.copy(uploading = false) }
            }
        }
    }

    private suspend fun uploadAndSend(
        bytes: ByteArray,
        mime: String,
        name: String,
        kind: String,
        durationMs: Int?,
    ) {
        val current = session.value
        val me = current.me ?: return
        val conversationId = me.conversationId
        if (conversationId == null) {
            extras.update { it.copy(error = "Hãy ghép đôi trước khi gửi") }
            return
        }
        if (bytes.size > MAX_UPLOAD_BYTES) {
            extras.update { it.copy(error = "Tệp quá lớn (tối đa 25 MB)") }
            return
        }

        extras.update { it.copy(uploading = true, error = null) }
        try {
            val attachment = uploadAttachment(conversationId, bytes, mime, name)
            val clientId = UUID.randomUUID().toString()
            val outgoing = OutgoingMessage(
                clientId = clientId,
                conversationId = conversationId,
                senderId = me.id,
                body = "",
                kind = kind,
                attachment = attachment,
                durationMs = durationMs,
            )
            val pending = Message(
                clientId = clientId,
                senderId = me.id,
                body = "",
                sentAt = System.currentTimeMillis(),
                status = MessageStatus.PENDING,
                kind = kind,
                attachmentPath = attachment.path,
                attachmentName = attachment.name,
                attachmentMime = attachment.mime,
                attachmentSize = attachment.size,
                durationMs = durationMs,
            )
            deliver(outgoing, pending)
        } catch (error: Throwable) {
            extras.update { it.copy(error = error.message ?: "Không tải được tệp lên") }
        } finally {
            extras.update { it.copy(uploading = false) }
        }
    }

    private suspend fun uploadAttachment(
        conversationId: String,
        bytes: ByteArray,
        mime: String,
        name: String,
    ): Attachment {
        val safeName = name.ifBlank { "tep" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .takeLast(60)
            .ifBlank { "tep" }
        val path = "$conversationId/${UUID.randomUUID()}-$safeName"
        gateway.uploadMedia(path, mime, bytes)
        return Attachment(path = path, name = name.ifBlank { safeName }, mime = mime, size = bytes.size.toLong())
    }

    fun retry(message: Message) {
        val me = session.value.me ?: return
        val conversationId = me.conversationId ?: return
        val outgoing = OutgoingMessage(
            clientId = message.clientId,
            conversationId = conversationId,
            senderId = me.id,
            body = message.body,
            kind = message.kind,
            sticker = message.sticker,
            attachment = message.attachmentPath?.let { path ->
                Attachment(
                    path = path,
                    name = message.attachmentName.orEmpty(),
                    mime = message.attachmentMime.orEmpty(),
                    size = message.attachmentSize ?: 0L,
                )
            },
            durationMs = message.durationMs,
        )
        viewModelScope.launch {
            repository.markPending(message.clientId)
            deliver(outgoing, message)
        }
    }

    private suspend fun deliver(outgoing: OutgoingMessage, pending: Message) {
        repository.addPending(pending)
        try {
            val remote = gateway.sendMessage(outgoing)
            repository.confirmSent(outgoing.clientId, remote)
        } catch (error: Throwable) {
            repository.markFailed(outgoing.clientId)
            extras.update { it.copy(error = error.message ?: "Không gửi được tin nhắn") }
        }
    }

    fun sendTyping() = gateway.sendTyping()

    // ------------------------------------------------------------- edit/delete

    fun beginEdit(message: Message) = extras.update { it.copy(editing = message) }

    fun cancelEdit() = extras.update { it.copy(editing = null) }

    fun saveEdit(text: String) {
        val editing = extras.value.editing ?: return
        val body = text.trim()
        if (body.isEmpty()) return
        extras.update { it.copy(editing = null) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.updateBody(editing.clientId, body, now)
            val serverId = editing.serverId ?: return@launch
            runCatching { gateway.editMessage(serverId, editing.senderId, body) }
                .onFailure { error ->
                    extras.update { it.copy(error = error.message ?: "Không sửa được tin nhắn") }
                }
        }
    }

    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.softDelete(message.clientId, now)
            val serverId = message.serverId ?: return@launch
            runCatching { gateway.deleteMessage(serverId, message.senderId) }
                .onFailure { error ->
                    extras.update { it.copy(error = error.message ?: "Không thu hồi được tin nhắn") }
                }
        }
    }

    // ----------------------------------------------------------------- search

    fun setSearchQuery(value: String) {
        searchQuery.value = value
        extras.update { it.copy(searchQuery = value) }
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

    // ------------------------------------------------------- media & playback

    /** Bitmap for an image message; null while loading or when it cannot load. */
    suspend fun loadImage(path: String): Bitmap? = withContext(Dispatchers.IO) {
        imageCache.get(path)?.let { return@withContext it }
        val file = ensureDownloaded(path) ?: return@withContext null
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return@withContext null
        imageCache.put(path, bitmap)
        bitmap
    }

    /** Downloads an attachment into the cache dir so it can be opened/shared. */
    suspend fun fileFor(path: String): File? = withContext(Dispatchers.IO) { ensureDownloaded(path) }

    private suspend fun ensureDownloaded(path: String): File? {
        val file = mediaFile(path)
        if (file.exists() && file.length() > 0) return file
        val bytes = runCatching { gateway.downloadMedia(path) }.getOrNull() ?: return null
        return runCatching {
            file.writeBytes(bytes)
            file.takeIf { it.length() > 0 }
        }.getOrNull()
    }

    private fun mediaFile(path: String): File {
        val dir = File(getApplication<Application>().cacheDir, "media").apply { mkdirs() }
        return File(dir, path.replace(Regex("[^A-Za-z0-9._-]"), "_"))
    }

    fun toggleVoice(message: Message) {
        if (extras.value.playingClientId == message.clientId) {
            stopVoice()
            return
        }
        val path = message.attachmentPath ?: return
        viewModelScope.launch {
            stopVoice()
            val file = ensureDownloaded(path) ?: run {
                extras.update { it.copy(error = "Không tải được bản ghi âm") }
                return@launch
            }
            val media = MediaPlayer()
            try {
                withContext(Dispatchers.IO) {
                    media.setDataSource(file.absolutePath)
                    media.prepare()
                }
                media.start()
            } catch (error: Throwable) {
                runCatching { media.release() }
                extras.update { it.copy(error = "Không phát được bản ghi âm") }
                return@launch
            }
            player = media
            extras.update { it.copy(playingClientId = message.clientId, playProgress = 0f) }
            val duration = (message.durationMs ?: media.duration).coerceAtLeast(1)
            playerJob = viewModelScope.launch {
                while (isActive) {
                    val position = runCatching { media.currentPosition }.getOrDefault(0)
                    val playing = runCatching { media.isPlaying }.getOrDefault(false)
                    extras.update {
                        it.copy(playProgress = (position.toFloat() / duration).coerceIn(0f, 1f))
                    }
                    if (!playing && position == 0) break
                    if (!playing) break
                    delay(120)
                }
                stopVoice()
            }
        }
    }

    fun stopVoice() {
        playerJob?.cancel()
        playerJob = null
        val current = player
        player = null
        if (current != null) {
            runCatching { current.stop() }
            runCatching { current.release() }
        }
        extras.update { it.copy(playingClientId = null, playProgress = 0f) }
    }

    // ------------------------------------------------- foreground / unread

    fun setForeground(value: Boolean) {
        foreground = value
        if (value) {
            unread = 0
            Notify.clear(getApplication<Application>())
        }
    }

    fun markRead() {
        unread = 0
        Notify.clear(getApplication<Application>())
        val current = session.value
        val me = current.me ?: return
        val conversationId = me.conversationId ?: return
        val last = messages.value.lastOrNull { it.senderId != me.id && it.serverId != null } ?: return
        val serverId = last.serverId ?: return
        viewModelScope.launch { runCatching { gateway.markRead(conversationId, me.id, serverId) } }
    }

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
                    if (!foreground) {
                        unread += 1
                        Notify.showMessage(
                            context = getApplication<Application>(),
                            partnerName = session.value.partner?.displayName ?: "Người ấy",
                            text = event.message.preview,
                            unread = unread,
                        )
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

    // ------------------------------------------------------------------ files

    private class Payload(val bytes: ByteArray, val mime: String, val name: String)

    private fun readUri(uri: Uri, compress: Boolean): Payload {
        val resolver = getApplication<Application>().contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        var name = "tep"
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
            }
        }

        if (compress && mime.startsWith("image/")) {
            val bitmap = runCatching {
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            if (bitmap != null) {
                val scaled = scaleDown(bitmap, MAX_IMAGE_EDGE)
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
                return Payload(
                    bytes = out.toByteArray(),
                    mime = "image/jpeg",
                    name = name.substringBeforeLast('.', name) + ".jpg",
                )
            }
        }

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Không đọc được tệp")
        return Payload(bytes, mime, name)
    }

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private companion object {
        const val MAX_UPLOAD_BYTES = 25 * 1024 * 1024
        const val MAX_IMAGE_EDGE = 1600
    }
}
