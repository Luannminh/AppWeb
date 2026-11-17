package com.twchat.app.data.remote

import com.twchat.app.data.Person
import com.twchat.app.data.PersonJson
import com.twchat.app.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Thin REST client for the TwoChat server (see `server/src/index.js`).
 * JSON in / JSON out, no code generation — the payloads are tiny.
 */
class TwoChatApi {

    @Volatile
    var baseUrl: String = Session.DEFAULT_SERVER_URL

    @Volatile
    var token: String? = null

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** HTTP client for regular calls. */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** WebSocket client: no read timeout, the socket is long lived. */
    val webSockets: OkHttpClient = http.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    // ------------------------------------------------------------------ auth

    suspend fun register(username: String, password: String, displayName: String): AuthResult {
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("displayName", displayName)
        return parseAuth(request("POST", "/api/auth/register", body, authenticated = false))
    }

    suspend fun login(username: String, password: String): AuthResult {
        val body = JSONObject().put("username", username).put("password", password)
        return parseAuth(request("POST", "/api/auth/login", body, authenticated = false))
    }

    suspend fun logout() {
        request("POST", "/api/auth/logout")
    }

    suspend fun me(): Pair<Person, Person?> {
        val json = request("GET", "/api/me")
        return PersonJson.parse(json.optJSONObject("user"))?.let { me ->
            me to PersonJson.parse(json.optJSONObject("partner"))
        } ?: throw ApiException(500, "Phản hồi không hợp lệ từ máy chủ")
    }

    // -------------------------------------------------------------- pairing

    suspend fun pairCode(): String = request("GET", "/api/pair/code").optString("code")

    suspend fun pair(code: String): Person? {
        val json = request("POST", "/api/pair", JSONObject().put("code", code))
        return PersonJson.parse(json.optJSONObject("partner"))
    }

    suspend fun unpair() {
        request("POST", "/api/pair/reset")
    }

    // ------------------------------------------------------------- messages

    suspend fun conversation(): ConversationSnapshot {
        val json = request("GET", "/api/conversation")
        val me = PersonJson.parse(json.optJSONObject("user"))
            ?: throw ApiException(500, "Phản hồi không hợp lệ từ máy chủ")
        return ConversationSnapshot(
            me = me,
            partner = PersonJson.parse(json.optJSONObject("partner")),
            messages = RemoteJson.messages(json.optJSONArray("messages")),
        )
    }

    suspend fun sendMessage(body: String, clientId: String): RemoteMessage {
        val payload = JSONObject().put("body", body).put("clientId", clientId)
        val json = request("POST", "/api/messages", payload)
        val message = json.optJSONObject("message")
            ?: throw ApiException(500, "Phản hồi không hợp lệ từ máy chủ")
        return RemoteJson.message(message)
    }

    suspend fun markRead(lastMessageId: Long) {
        request("POST", "/api/read", JSONObject().put("lastMessageId", lastMessageId))
    }

    suspend fun sendTyping() {
        request("POST", "/api/typing")
    }

    suspend fun updateProfile(displayName: String?, color: Long?): Person? {
        val payload = JSONObject()
        if (displayName != null) payload.put("displayName", displayName)
        if (color != null) payload.put("color", color)
        val json = request("POST", "/api/profile", payload)
        return PersonJson.parse(json.optJSONObject("user"))
    }

    // ------------------------------------------------------------- websocket

    fun webSocketUrl(): String? {
        val currentToken = token?.takeIf { it.isNotBlank() } ?: return null
        val base = baseUrl.trim().trimEnd('/')
        if (base.isBlank()) return null
        val socketBase = when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
            base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
            else -> "ws://$base"
        }
        val encoded = URLEncoder.encode(currentToken, "UTF-8")
        return "$socketBase/ws?token=$encoded"
    }

    fun openSocket(listener: WebSocketListener): WebSocket? {
        val url = webSocketUrl() ?: return null
        val request = runCatching { Request.Builder().url(url).build() }.getOrNull() ?: return null
        return webSockets.newWebSocket(request, listener)
    }

    // ---------------------------------------------------------------- plumbing

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        authenticated: Boolean = true,
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = baseUrl.trim().trimEnd('/') + path
        val builder = try {
            Request.Builder().url(url)
        } catch (error: IllegalArgumentException) {
            throw ApiException(0, "Địa chỉ máy chủ không hợp lệ")
        }
        val currentToken = token
        if (authenticated && !currentToken.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $currentToken")
        }
        if (method == "GET") {
            builder.get()
        } else {
            val payload = (body ?: JSONObject()).toString()
            builder.method(method, payload.toRequestBody(jsonMediaType))
        }

        val response = try {
            http.newCall(builder.build()).execute()
        } catch (error: IOException) {
            throw ApiException(0, "Không kết nối được máy chủ. Kiểm tra địa chỉ và mạng.")
        }

        response.use {
            val text = it.body?.string().orEmpty()
            val json = runCatching { if (text.isBlank()) JSONObject() else JSONObject(text) }
                .getOrElse { JSONObject() }
            if (!it.isSuccessful) {
                val message = json.optString("error").ifBlank { "Máy chủ trả về lỗi ${it.code}" }
                throw ApiException(it.code, message)
            }
            json
        }
    }

    private fun parseAuth(json: JSONObject): AuthResult {
        val me = PersonJson.parse(json.optJSONObject("user"))
            ?: throw ApiException(500, "Phản hồi không hợp lệ từ máy chủ")
        val token = json.optString("token")
        if (token.isBlank()) throw ApiException(500, "Máy chủ không trả về token")
        return AuthResult(token = token, me = me, partner = PersonJson.parse(json.optJSONObject("partner")))
    }
}
