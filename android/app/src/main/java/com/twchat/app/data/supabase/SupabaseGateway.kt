package com.twchat.app.data.supabase

import android.util.Log
import com.twchat.app.data.Person
import com.twchat.app.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

data class ConversationSnapshot(
    val me: Person,
    val partner: Person?,
    val messages: List<RemoteMessage>,
)

/**
 * Everything the app needs from Supabase in one place: auth, reads/writes and
 * the realtime channel. The ViewModel talks to this class, never to the API.
 */
class SupabaseGateway(private val sessionStore: SessionStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val api = SupabaseApi()
    private val realtime = SupabaseRealtime(api)

    val events: SharedFlow<RealtimeEvent> = realtime.events
    val isRealtimeConnected: Boolean get() = realtime.isConnected

    init {
        api.onTokensRefreshed = { tokens ->
            scope.launch {
                sessionStore.saveTokens(tokens.accessToken, tokens.refreshToken, tokens.expiresAt)
                realtime.updateAccessToken(tokens.accessToken)
            }
        }
    }

    // ------------------------------------------------------------- lifecycle

    fun configure(serverUrl: String, anonKey: String) {
        api.configure(serverUrl, anonKey)
    }

    fun applyTokens(tokenSet: TokenSet?) {
        api.updateTokens(tokenSet)
    }

    fun startRealtime(conversationId: String, myId: String, partnerId: String?) {
        realtime.start(conversationId, myId, partnerId)
    }

    fun stopRealtime() = realtime.stop()

    fun stop() {
        realtime.stop()
        api.updateTokens(null)
    }

    // ------------------------------------------------------------------ auth

    suspend fun signInWithEmail(email: String, password: String): TokenSet =
        api.signInWithEmail(email, password)

    suspend fun signUpWithEmail(email: String, password: String, displayName: String): AuthResult =
        api.signUpWithEmail(email, password, displayName)

    suspend fun signInAnonymously(displayName: String): TokenSet =
        api.signInAnonymously(displayName)

    suspend fun signOut() = api.signOut()

    // ------------------------------------------------------------------ data

    suspend fun loadConversation(userId: String): ConversationSnapshot {
        val me = api.profile(userId)
            ?: throw SupabaseException(
                404,
                "Không tìm thấy hồ sơ. Kiểm tra đã chạy supabase/schema.sql chưa?",
            )
        val conversationId = me.conversationId
        val partner = conversationId?.let { api.partnerProfile(it, userId) }
        val messages = conversationId?.let { api.messages(it) }.orEmpty()
        return ConversationSnapshot(me = me, partner = partner, messages = messages)
    }

    suspend fun refreshMessages(conversationId: String): List<RemoteMessage> =
        api.messages(conversationId)

    suspend fun sendMessage(outgoing: OutgoingMessage): RemoteMessage = api.sendMessage(outgoing)

    suspend fun editMessage(messageId: Long, senderId: String, body: String) =
        api.editMessage(messageId, senderId, body)

    suspend fun deleteMessage(messageId: Long, senderId: String) =
        api.deleteMessage(messageId, senderId)

    suspend fun uploadMedia(path: String, mime: String, bytes: ByteArray) =
        api.uploadMedia(path, mime, bytes)

    suspend fun downloadMedia(path: String): ByteArray = api.downloadMedia(path)

    suspend fun markRead(conversationId: String, myId: String, upToId: Long) =
        api.markRead(conversationId, myId, upToId)

    suspend fun pairWithCode(code: String) = api.pairWithCode(code)

    suspend fun unpair() = api.unpair()

    suspend fun updateProfile(userId: String, displayName: String?, color: Long?) =
        api.updateProfile(userId, displayName, color)

    suspend fun touchLastSeen(userId: String) {
        runCatching { api.touchLastSeen(userId) }
            .onFailure { Log.d(TAG, "touchLastSeen failed: ${it.message}") }
    }

    // -------------------------------------------------------------- realtime

    fun sendTyping() = realtime.sendTyping()

    private companion object {
        const val TAG = "SupabaseGateway"
    }
}
