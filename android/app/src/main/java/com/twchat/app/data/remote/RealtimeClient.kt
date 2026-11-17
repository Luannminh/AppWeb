package com.twchat.app.data.remote

import com.twchat.app.data.Person
import com.twchat.app.data.PersonJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

sealed interface SocketEvent {
    data object Connected : SocketEvent
    data object Disconnected : SocketEvent
    data object Unauthorized : SocketEvent

    data class Hello(
        val me: Person,
        val partner: Person?,
        val messages: List<RemoteMessage>,
    ) : SocketEvent

    data class Incoming(val message: RemoteMessage) : SocketEvent

    data class Read(val userId: Long, val lastMessageId: Long, val readAt: Long) : SocketEvent

    data class Typing(val userId: Long) : SocketEvent

    data class Presence(val userId: Long, val online: Boolean, val lastSeen: Long) : SocketEvent

    data class Paired(val partner: Person?) : SocketEvent

    data object Unpaired : SocketEvent

    data class ProfileUpdated(val user: Person?) : SocketEvent
}

/**
 * Keeps a WebSocket to the TwoChat server alive and turns server frames into
 * [SocketEvent]s. Reconnects with exponential backoff while the user is logged in.
 */
class RealtimeClient(private val api: TwoChatApi) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<SocketEvent> = _events.asSharedFlow()

    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var attempts = 0
    private var stopped = true

    @Volatile
    var isConnected: Boolean = false
        private set

    fun start() {
        stopped = false
        attempts = 0
        openSocket()
    }

    fun stop() {
        stopped = true
        reconnectJob?.cancel()
        reconnectJob = null
        socket?.close(1000, "bye")
        socket = null
        isConnected = false
    }

    fun sendTyping() {
        if (isConnected) socket?.send("""{"type":"typing"}""")
    }

    fun sendRead(lastMessageId: Long) {
        if (isConnected) socket?.send("""{"type":"read","lastMessageId":$lastMessageId}""")
    }

    private fun openSocket() {
        val webSocket = api.openSocket(listener)
        if (webSocket == null) {
            scheduleReconnect()
            return
        }
        socket = webSocket
    }

    private fun scheduleReconnect() {
        if (stopped) return
        reconnectJob?.cancel()
        val delayMs = minOf(15_000L, 800L * (1L shl minOf(attempts, 4)))
        attempts += 1
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!stopped) openSocket()
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            isConnected = true
            attempts = 0
            _events.tryEmit(SocketEvent.Connected)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            parse(text)?.let { _events.tryEmit(it) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            handleDrop(code)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            handleDrop(response?.code ?: 0)
        }
    }

    private fun handleDrop(code: Int) {
        isConnected = false
        socket = null
        if (code == 4001) {
            // Token rejected: reconnecting would loop forever.
            stopped = true
            _events.tryEmit(SocketEvent.Unauthorized)
            return
        }
        _events.tryEmit(SocketEvent.Disconnected)
        scheduleReconnect()
    }

    private fun parse(raw: String): SocketEvent? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return when (json.optString("type")) {
            "hello" -> SocketEvent.Hello(
                me = PersonJson.parse(json.optJSONObject("user")) ?: return null,
                partner = PersonJson.parse(json.optJSONObject("partner")),
                messages = RemoteJson.messages(json.optJSONArray("messages")),
            )

            "message" -> json.optJSONObject("message")?.let { SocketEvent.Incoming(RemoteJson.message(it)) }

            "read" -> SocketEvent.Read(
                userId = json.optLong("userId"),
                lastMessageId = json.optLong("lastMessageId"),
                readAt = json.optLong("readAt"),
            )

            "typing" -> SocketEvent.Typing(json.optLong("userId"))

            "presence" -> SocketEvent.Presence(
                userId = json.optLong("userId"),
                online = json.optBoolean("online"),
                lastSeen = json.optLong("lastSeen"),
            )

            "paired" -> SocketEvent.Paired(PersonJson.parse(json.optJSONObject("partner")))

            "unpaired" -> SocketEvent.Unpaired

            "profile" -> SocketEvent.ProfileUpdated(PersonJson.parse(json.optJSONObject("user")))

            "pong" -> null

            else -> null
        }
    }
}
