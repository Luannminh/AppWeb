package com.twchat.app.data.supabase

import com.twchat.app.data.Person
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

sealed interface RealtimeEvent {
    data object Connected : RealtimeEvent
    data object Disconnected : RealtimeEvent

    data class MessageInserted(val message: RemoteMessage) : RealtimeEvent
    data class MessageUpdated(val message: RemoteMessage) : RealtimeEvent

    data class PartnerUpdated(val person: Person) : RealtimeEvent

    /** The other person is typing (best effort, over Realtime Broadcast). */
    data class Typing(val from: String) : RealtimeEvent

    /** Heartbeat broadcast: the other person's app is open right now. */
    data class Presence(val from: String) : RealtimeEvent
}

/**
 * Supabase Realtime over a raw Phoenix WebSocket:
 *
 *  - `postgres_changes` on `messages` (filtered by conversation_id) and on
 *    `profiles` (filtered by the partner id) for inserts / read receipts;
 *  - `broadcast` messages for the typing indicator and a presence heartbeat.
 *
 * Transport is the same OkHttp client used for REST, so no extra dependency.
 */
class SupabaseRealtime(private val api: SupabaseApi) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    private val refs = AtomicInteger(0)

    private var socket: WebSocket? = null
    private var topic: String? = null
    private var conversationId: String? = null
    private var myId: String? = null
    private var partnerId: String? = null

    private var heartbeatJob: Job? = null
    private var presenceJob: Job? = null
    private var reconnectJob: Job? = null

    private var attempts = 0
    private var stopped = true
    private var joined = false

    @Volatile
    var isConnected: Boolean = false
        private set

    fun start(conversationId: String, myId: String, partnerId: String?) {
        socket?.close(1000, "restart")
        socket = null
        joined = false
        isConnected = false
        heartbeatJob?.cancel()
        presenceJob?.cancel()
        this.conversationId = conversationId
        this.myId = myId
        this.partnerId = partnerId
        this.topic = "realtime:chat:$conversationId"
        stopped = false
        attempts = 0
        openSocket()
    }

    fun stop() {
        stopped = true
        joined = false
        isConnected = false
        heartbeatJob?.cancel()
        presenceJob?.cancel()
        reconnectJob?.cancel()
        heartbeatJob = null
        presenceJob = null
        reconnectJob = null
        socket?.close(1000, "bye")
        socket = null
    }

    fun sendTyping() = broadcast("typing")

    fun sendPresence() = broadcast("presence")

    /** Keeps RLS working on the channel after a token refresh. */
    fun updateAccessToken(accessToken: String) {
        val current = topic ?: return
        sendRaw(
            JSONObject()
                .put("topic", current)
                .put("event", "access_token")
                .put("payload", JSONObject().put("access_token", accessToken))
                .put("ref", nextRef()),
        )
    }

    // ------------------------------------------------------------- internals

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
        val wait = minOf(15_000L, 800L * (1L shl minOf(attempts, 4)))
        attempts += 1
        reconnectJob = scope.launch {
            delay(wait)
            if (!stopped) openSocket()
        }
    }

    private fun nextRef(): String = refs.incrementAndGet().toString()

    private fun sendRaw(json: JSONObject): Boolean {
        val current = socket ?: return false
        return current.send(json.toString())
    }

    private fun joinChannel() {
        val conversation = conversationId ?: return
        val currentTopic = topic ?: return
        val accessToken = api.tokens?.accessToken.orEmpty()

        val changes = JSONArray().apply {
            put(
                JSONObject()
                    .put("event", "*")
                    .put("schema", "public")
                    .put("table", "messages")
                    .put("filter", "conversation_id=eq.$conversation"),
            )
            partnerId?.let { partner ->
                put(
                    JSONObject()
                        .put("event", "UPDATE")
                        .put("schema", "public")
                        .put("table", "profiles")
                        .put("filter", "id=eq.$partner"),
                )
            }
        }

        val payload = JSONObject()
            .put(
                "config",
                JSONObject()
                    .put("broadcast", JSONObject().put("self", false).put("ack", false))
                    .put("presence", JSONObject().put("key", myId.orEmpty()))
                    .put("postgres_changes", changes),
            )
            .put("access_token", accessToken)

        val joinRef = nextRef()
        sendRaw(
            JSONObject()
                .put("topic", currentTopic)
                .put("event", "phx_join")
                .put("payload", payload)
                .put("ref", joinRef)
                .put("join_ref", joinRef),
        )
    }

    private fun markJoined() {
        if (joined) return
        joined = true
        isConnected = true
        attempts = 0
        _events.tryEmit(RealtimeEvent.Connected)
        startLoops()
    }

    private fun startLoops() {
        heartbeatJob?.cancel()
        presenceJob?.cancel()

        heartbeatJob = scope.launch {
            while (isActive) {
                delay(20_000)
                sendRaw(
                    JSONObject()
                        .put("topic", "phoenix")
                        .put("event", "heartbeat")
                        .put("payload", JSONObject())
                        .put("ref", nextRef()),
                )
            }
        }

        presenceJob = scope.launch {
            while (isActive) {
                broadcast("presence")
                delay(15_000)
            }
        }
    }

    private fun broadcast(name: String) {
        if (!isConnected) return
        val currentTopic = topic ?: return
        val payload = JSONObject()
            .put("type", "broadcast")
            .put("event", name)
            .put("payload", JSONObject().put("from", myId.orEmpty()))
        sendRaw(
            JSONObject()
                .put("topic", currentTopic)
                .put("event", "broadcast")
                .put("payload", payload)
                .put("ref", nextRef()),
        )
    }

    /** Called when the socket goes away; ignores callbacks from an old socket. */
    private fun handleDrop(from: WebSocket?) {
        if (from != null && socket != null && socket !== from) return
        joined = false
        isConnected = false
        heartbeatJob?.cancel()
        presenceJob?.cancel()
        socket = null
        if (stopped) return
        _events.tryEmit(RealtimeEvent.Disconnected)
        scheduleReconnect()
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            joinChannel()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (json.optString("event")) {
                "phx_reply" -> {
                    val reply = json.optJSONObject("payload") ?: return
                    if (reply.optString("status") == "ok") {
                        markJoined()
                    } else {
                        // Usually an RLS / filter problem: retry with backoff.
                        handleDrop(webSocket)
                    }
                }

                "postgres_changes" -> handlePostgresChange(json)

                "broadcast" -> {
                    val payload = json.optJSONObject("payload") ?: return
                    val from = payload.optJSONObject("payload")?.optString("from").orEmpty()
                    if (from.isEmpty() || from == myId) return
                    when (payload.optString("event")) {
                        "typing" -> _events.tryEmit(RealtimeEvent.Typing(from))
                        "presence" -> _events.tryEmit(RealtimeEvent.Presence(from))
                    }
                }

                "phx_error", "phx_close" -> handleDrop(webSocket)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            handleDrop(webSocket)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            handleDrop(webSocket)
        }
    }

    private fun handlePostgresChange(json: JSONObject) {
        val data = json.optJSONObject("payload")?.optJSONObject("data") ?: return
        val table = data.optString("table")
        // Servers in the wild send either type/record or eventType/new.
        val type = data.optString("type").ifBlank { data.optString("eventType") }
        val record = data.optJSONObject("record")
            ?: data.optJSONObject("new")
            ?: return

        when (table) {
            "messages" -> {
                val message = runCatching { SupabaseMapping.message(record) }.getOrNull() ?: return
                if (type.equals("INSERT", true)) {
                    _events.tryEmit(RealtimeEvent.MessageInserted(message))
                } else {
                    _events.tryEmit(RealtimeEvent.MessageUpdated(message))
                }
            }

            "profiles" -> {
                val person = runCatching { SupabaseMapping.person(record) }.getOrNull() ?: return
                _events.tryEmit(RealtimeEvent.PartnerUpdated(person))
            }
        }
    }
}
