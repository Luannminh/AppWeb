package com.twchat.app.data.supabase

/** Supabase auth tokens for one signed-in user. */
data class TokenSet(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String,
    val email: String?,
    val isAnonymous: Boolean,
) {
    val needsRefresh: Boolean get() = expiresAt > 0 && expiresAt - System.currentTimeMillis() < 60_000
}

sealed interface AuthResult {
    data class LoggedIn(val tokens: TokenSet) : AuthResult

    /** Email sign-up with "Confirm email" enabled: nothing to store yet. */
    data object ConfirmEmail : AuthResult
}

/** Anything Supabase answers with a non-2xx status. */
class SupabaseException(val status: Int, override val message: String) : Exception(message)

/** Where an attachment lives inside the `chat-media` bucket. */
data class Attachment(
    val path: String,
    val name: String,
    val mime: String,
    val size: Long,
)

/** A message the app is about to insert. */
data class OutgoingMessage(
    val clientId: String,
    val conversationId: String,
    val senderId: String,
    val body: String = "",
    val kind: String = MessageKindFields.TEXT,
    val sticker: String? = null,
    val attachment: Attachment? = null,
    val durationMs: Int? = null,
)

/** One row of `public.messages`. */
data class RemoteMessage(
    val id: Long,
    val clientId: String,
    val senderId: String,
    val body: String,
    val createdAt: Long,
    val readAt: Long?,
    val kind: String = MessageKindFields.TEXT,
    val sticker: String? = null,
    val attachmentPath: String? = null,
    val attachmentName: String? = null,
    val attachmentMime: String? = null,
    val attachmentSize: Long? = null,
    val durationMs: Int? = null,
    val editedAt: Long? = null,
    val deletedAt: Long? = null,
) {
    /** Text used by notifications. */
    val preview: String
        get() = when {
            deletedAt != null -> "Tin nhắn đã được thu hồi"
            kind == MessageKindFields.STICKER -> sticker ?: body
            kind == MessageKindFields.IMAGE -> "📷 Ảnh"
            kind == MessageKindFields.VOICE -> "🎤 Tin nhắn thoại"
            kind == MessageKindFields.FILE -> "📎 ${attachmentName ?: "Tệp"}"
            else -> body
        }
}

/** Kind values, kept in sync with the `messages_kind_check` constraint. */
object MessageKindFields {
    const val TEXT = "text"
    const val STICKER = "sticker"
    const val IMAGE = "image"
    const val FILE = "file"
    const val VOICE = "voice"
}
