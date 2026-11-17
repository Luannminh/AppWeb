package com.twchat.app.data.supabase

import com.twchat.app.data.Person
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Direct Supabase client (no SDK): GoTrue for auth, PostgREST for data.
 *
 * Tokens are refreshed lazily: every call checks the expiry, and a 401 triggers
 * one silent refresh + retry before giving up.
 */
class SupabaseApi {

    @Volatile
    var baseUrl: String = ""
        private set

    @Volatile
    var anonKey: String = ""
        private set

    @Volatile
    var tokens: TokenSet? = null

    /** Fired after a silent token refresh so the caller can persist the new tokens. */
    @Volatile
    var onTokensRefreshed: ((TokenSet) -> Unit)? = null

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val refreshMutex = Mutex()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    /** Long-lived client for the Realtime WebSocket. */
    val sockets: OkHttpClient = http.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    // ------------------------------------------------------------- lifecycle

    fun configure(serverUrl: String, key: String) {
        baseUrl = normalizeUrl(serverUrl)
        anonKey = key.trim()
    }

    fun updateTokens(newTokens: TokenSet?) {
        tokens = newTokens
    }

    val isConfigured: Boolean get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    private fun requireConfigured() {
        if (!isConfigured) {
            throw SupabaseException(0, "Chưa cấu hình Supabase URL và anon key")
        }
    }

    private fun normalizeUrl(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return ""
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        return url.trimEnd('/')
    }

    // ------------------------------------------------------------------ auth

    suspend fun signUpWithEmail(email: String, password: String, displayName: String): AuthResult {
        requireConfigured()
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .put("data", JSONObject().put("display_name", displayName.trim()))
        val json = JSONObject(execute("POST", "${baseUrl}/auth/v1/signup", body, bearer = null, apikey = true))
        val session = parseSession(json)
        return if (session != null) {
            tokens = session
            AuthResult.LoggedIn(session)
        } else {
            AuthResult.ConfirmEmail
        }
    }

    suspend fun signInWithEmail(email: String, password: String): TokenSet {
        requireConfigured()
        val body = JSONObject().put("email", email.trim()).put("password", password)
        val text = execute("POST", "${baseUrl}/auth/v1/token?grant_type=password", body, bearer = null, apikey = true)
        val session = parseSession(JSONObject(text))
            ?: throw SupabaseException(401, "Không nhận được phiên đăng nhập từ Supabase")
        tokens = session
        return session
    }

    suspend fun signInAnonymously(displayName: String): TokenSet {
        requireConfigured()
        val body = JSONObject().put("data", JSONObject().put("display_name", displayName.trim()))
        val text = execute("POST", "${baseUrl}/auth/v1/signup", body, bearer = null, apikey = true)
        val session = parseSession(JSONObject(text))
            ?: throw SupabaseException(
                400,
                "Supabase chưa bật đăng nhập ẩn danh. Vào Authentication → Sign In / Providers → " +
                    "bật \"Allow anonymous sign-ins\" rồi thử lại.",
            )
        tokens = session
        return session
    }

    suspend fun signOut() {
        val token = tokens?.accessToken
        if (token != null) {
            runCatching { execute("POST", "${baseUrl}/auth/v1/logout", JSONObject(), bearer = token, apikey = true) }
        }
        tokens = null
    }

    // ----------------------------------------------------------- data (REST)

    suspend fun profile(userId: String): Person? =
        jsonArray(rest("GET", "/rest/v1/profiles?id=eq.$userId&select=*"))
            .firstOrNull()
            ?.let(SupabaseMapping::person)

    suspend fun partnerProfile(conversationId: String, myId: String): Person? =
        jsonArray(rest("GET", "/rest/v1/profiles?conversation_id=eq.$conversationId&id=neq.$myId&select=*"))
            .firstOrNull()
            ?.let(SupabaseMapping::person)

    suspend fun messages(conversationId: String, limit: Int = 300): List<RemoteMessage> =
        jsonArray(
            rest(
                "GET",
                "/rest/v1/messages?conversation_id=eq.$conversationId" +
                    "&select=${SupabaseMapping.messageSelect}&order=id.desc&limit=$limit",
            ),
        ).mapNotNull { row -> runCatching { SupabaseMapping.message(row) }.getOrNull() }
            .sortedBy { it.id }

    suspend fun sendMessage(outgoing: OutgoingMessage): RemoteMessage {
        val payload = JSONObject()
            .put("conversation_id", outgoing.conversationId)
            .put("sender_id", outgoing.senderId)
            .put("client_id", outgoing.clientId)
            .put("body", outgoing.body)
            .put("kind", outgoing.kind)
        outgoing.sticker?.let { payload.put("sticker", it) }
        outgoing.durationMs?.let { payload.put("duration_ms", it) }
        outgoing.attachment?.let { attachment ->
            payload.put("attachment_path", attachment.path)
            payload.put("attachment_name", attachment.name)
            payload.put("attachment_mime", attachment.mime)
            payload.put("attachment_size", attachment.size)
        }

        val text = rest(
            method = "POST",
            path = "/rest/v1/messages?on_conflict=conversation_id,sender_id,client_id" +
                "&select=${SupabaseMapping.messageSelect}",
            body = payload,
            prefer = "resolution=merge-duplicates,return=representation",
        )
        return jsonArray(text).firstOrNull()?.let(SupabaseMapping::message)
            ?: throw SupabaseException(500, "Không gửi được tin nhắn")
    }

    suspend fun editMessage(messageId: Long, senderId: String, body: String) {
        rest(
            method = "PATCH",
            path = "/rest/v1/messages?id=eq.$messageId&sender_id=eq.$senderId",
            body = JSONObject()
                .put("body", body)
                .put("edited_at", SupabaseMapping.millisToIso(System.currentTimeMillis())),
            prefer = "return=minimal",
        )
    }

    /** Soft delete: the partner sees "Tin nhắn đã được thu hồi". */
    suspend fun deleteMessage(messageId: Long, senderId: String) {
        rest(
            method = "PATCH",
            path = "/rest/v1/messages?id=eq.$messageId&sender_id=eq.$senderId",
            body = JSONObject()
                .put("body", "")
                .put("deleted_at", SupabaseMapping.millisToIso(System.currentTimeMillis())),
            prefer = "return=minimal",
        )
    }

    // -------------------------------------------------------------- storage

    suspend fun uploadMedia(path: String, mime: String, bytes: ByteArray) {
        requireConfigured()
        val token = authToken()
        executeBytes(
            method = "POST",
            url = "${baseUrl}/storage/v1/object/chat-media/$path",
            body = bytes,
            contentType = mime,
            token = token.accessToken,
            headers = mapOf("x-upsert" to "false"),
        )
    }

    suspend fun downloadMedia(path: String): ByteArray {
        requireConfigured()
        val token = authToken()
        return executeBytes(
            method = "GET",
            url = "${baseUrl}/storage/v1/object/authenticated/chat-media/$path",
            body = null,
            contentType = null,
            token = token.accessToken,
        )
    }

    suspend fun markRead(conversationId: String, myId: String, upToId: Long) {
        rest(
            method = "PATCH",
            path = "/rest/v1/messages?conversation_id=eq.$conversationId&sender_id=neq.$myId" +
                "&id=lte.$upToId&read_at=is.null",
            body = JSONObject().put("read_at", SupabaseMapping.millisToIso(System.currentTimeMillis())),
            prefer = "return=minimal",
        )
    }

    suspend fun pairWithCode(code: String) {
        rest("POST", "/rest/v1/rpc/pair_with_code", JSONObject().put("code", code))
    }

    suspend fun unpair() {
        rest("POST", "/rest/v1/rpc/unpair", JSONObject())
    }

    suspend fun updateProfile(userId: String, displayName: String?, color: Long?) {
        val payload = JSONObject()
        if (!displayName.isNullOrBlank()) payload.put("display_name", displayName.trim())
        if (color != null) payload.put("color", color)
        if (payload.length() == 0) return
        rest("PATCH", "/rest/v1/profiles?id=eq.$userId", payload, prefer = "return=minimal")
    }

    suspend fun touchLastSeen(userId: String) {
        rest(
            method = "PATCH",
            path = "/rest/v1/profiles?id=eq.$userId",
            body = JSONObject().put("last_seen", SupabaseMapping.millisToIso(System.currentTimeMillis())),
            prefer = "return=minimal",
        )
    }

    // ------------------------------------------------------------- realtime

    fun realtimeUrl(): String? {
        if (!isConfigured) return null
        val socketBase = when {
            baseUrl.startsWith("https://") -> "wss://" + baseUrl.removePrefix("https://")
            baseUrl.startsWith("http://") -> "ws://" + baseUrl.removePrefix("http://")
            else -> return null
        }
        val key = URLEncoder.encode(anonKey, "UTF-8")
        return "$socketBase/realtime/v1/websocket?apikey=$key&vsn=1.0.0"
    }

    fun openSocket(listener: WebSocketListener): WebSocket? {
        val url = realtimeUrl() ?: return null
        val request = runCatching { Request.Builder().url(url).build() }.getOrNull() ?: return null
        return sockets.newWebSocket(request, listener)
    }

    // ------------------------------------------------------------- plumbing

    private suspend fun rest(
        method: String,
        path: String,
        body: JSONObject? = null,
        prefer: String? = null,
    ): String {
        requireConfigured()
        val token = authToken()
        val url = "${baseUrl}$path"
        return try {
            execute(method, url, body, bearer = token.accessToken, apikey = true, prefer = prefer)
        } catch (error: SupabaseException) {
            if (error.status != 401) throw error
            val refreshed = refreshTokens()
                ?: throw SupabaseException(401, "Phiên đăng nhập hết hạn, đăng nhập lại nhé")
            execute(method, url, body, bearer = refreshed.accessToken, apikey = true, prefer = prefer)
        }
    }

    private suspend fun refreshTokens(): TokenSet? {
        val stale = tokens ?: return null
        return refreshMutex.withLock {
            val current = tokens
            // Another coroutine already refreshed while we waited for the lock.
            if (current != null && current.accessToken != stale.accessToken) return@withLock current
            val refresh = current?.refreshToken?.takeIf { it.isNotBlank() } ?: return@withLock null
            val json = runCatching {
                JSONObject(
                    execute(
                        "POST",
                        "${baseUrl}/auth/v1/token?grant_type=refresh_token",
                        JSONObject().put("refresh_token", refresh),
                        bearer = null,
                        apikey = true,
                    ),
                )
            }.getOrNull() ?: return@withLock null
            val session = parseSession(json) ?: return@withLock null
            tokens = session
            onTokensRefreshed?.invoke(session)
            session
        }
    }

    private suspend fun authToken(): TokenSet {
        val current = tokens ?: throw SupabaseException(401, "Chưa đăng nhập")
        return if (current.needsRefresh) (refreshTokens() ?: current) else current
    }

    private suspend fun executeBytes(
        method: String,
        url: String,
        body: ByteArray?,
        contentType: String?,
        token: String,
        headers: Map<String, String> = emptyMap(),
    ): ByteArray = withContext(Dispatchers.IO) {
        val builder = try {
            Request.Builder().url(url)
        } catch (error: IllegalArgumentException) {
            throw SupabaseException(0, "Địa chỉ Supabase không hợp lệ")
        }
        builder.header("apikey", anonKey)
        builder.header("Authorization", "Bearer $token")
        headers.forEach { (name, value) -> builder.header(name, value) }
        if (method == "GET") {
            builder.get()
        } else {
            builder.method(
                method,
                (body ?: ByteArray(0)).toRequestBody(contentType?.toMediaTypeOrNull()),
            )
        }

        val response = try {
            http.newCall(builder.build()).execute()
        } catch (error: IOException) {
            throw SupabaseException(0, "Không kết nối được Supabase. Kiểm tra URL, anon key và mạng.")
        }

        response.use { res ->
            val bytes = res.body?.bytes() ?: ByteArray(0)
            if (!res.isSuccessful) {
                throw SupabaseException(res.code, translateError(res.code, String(bytes)))
            }
            bytes
        }
    }

    private suspend fun execute(
        method: String,
        url: String,
        body: JSONObject?,
        bearer: String?,
        apikey: Boolean,
        prefer: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val builder = try {
            Request.Builder().url(url)
        } catch (error: IllegalArgumentException) {
            throw SupabaseException(0, "Địa chỉ Supabase không hợp lệ")
        }
        if (apikey) builder.header("apikey", anonKey)
        if (!bearer.isNullOrBlank()) builder.header("Authorization", "Bearer $bearer")
        if (prefer != null) builder.header("Prefer", prefer)

        if (method == "GET") {
            builder.get()
        } else {
            builder.method(method, (body ?: JSONObject()).toString().toRequestBody(jsonMedia))
        }

        val response = try {
            http.newCall(builder.build()).execute()
        } catch (error: IOException) {
            throw SupabaseException(0, "Không kết nối được Supabase. Kiểm tra URL, anon key và mạng.")
        }

        response.use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw SupabaseException(res.code, translateError(res.code, text))
            text
        }
    }

    private fun parseSession(json: JSONObject): TokenSet? {
        val accessToken = json.optString("access_token")
        if (accessToken.isBlank()) return null
        val user = json.optJSONObject("user") ?: return null
        val userId = user.optString("id")
        if (userId.isBlank()) return null
        val refreshToken = json.optString("refresh_token")
        val expiresIn = json.optLong("expires_in", 3600L)
        val expiresAtSeconds = json.optLong("expires_at", 0L)
        val expiresAt = if (expiresAtSeconds > 0) {
            expiresAtSeconds * 1000
        } else {
            System.currentTimeMillis() + expiresIn * 1000
        }
        val email = user.optString("email").ifBlank { null }
        return TokenSet(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expiresAt,
            userId = userId,
            email = email,
            isAnonymous = user.optBoolean("is_anonymous", email == null),
        )
    }

    private fun jsonArray(text: String): List<JSONObject> {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed == "null") return emptyList()
        return runCatching {
            val array = JSONArray(trimmed)
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index) }
        }.getOrDefault(emptyList())
    }

    private fun translateError(status: Int, text: String): String {
        val json = runCatching { JSONObject(text) }.getOrNull()
        val code = json?.optString("error_code").orEmpty()
        val raw = listOf(
            json?.optString("message"),
            json?.optString("error_description"),
            json?.optString("msg"),
            json?.optString("error"),
            json?.optString("hint"),
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()

        return when (code) {
            "invalid_credentials" -> "Sai email hoặc mật khẩu"
            "email_not_confirmed" -> "Email chưa xác nhận. Mở hộp thư xác nhận rồi đăng nhập nhé."
            "user_already_exists", "email_exists" -> "Email này đã được đăng ký"
            "weak_password" -> "Mật khẩu quá yếu, cần ít nhất 6 ký tự"
            "anonymous_provider_disabled" -> "Chưa bật đăng nhập ẩn danh trong Supabase"
            "signup_disabled" -> "Supabase đang tắt đăng ký"
            "email_address_invalid" -> "Email không hợp lệ"
            "over_email_send_rate_limit" -> "Gửi email quá nhiều lần, thử lại sau ít phút"
            else -> when {
                raw.isNotBlank() -> raw
                status == 401 -> "Phiên đăng nhập hết hạn, đăng nhập lại nhé"
                status == 403 -> "Không có quyền truy cập. Kiểm tra RLS / đã chạy schema.sql chưa."
                else -> "Supabase trả về lỗi $status"
            }
        }
    }
}
