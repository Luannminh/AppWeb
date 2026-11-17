package com.twchat.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A message in the local cache. Postgres is the source of truth; this table
 * keeps the conversation instant to open, readable offline and lets a pending
 * message survive a restart. [clientId] is the stable identity of a message and
 * matches the `client_id` column, which makes retries idempotent.
 */
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey
    val clientId: String,
    /** Postgres `id`, null while the message is still pending. */
    val serverId: Long? = null,
    /** `auth.users.id` of the sender (uuid). */
    val senderId: String,
    val body: String,
    /** Epoch millis. */
    val sentAt: Long,
    val readAt: Long? = null,
    val status: String = MessageStatus.SENT,
    /** One of [MessageKind]. */
    val kind: String = MessageKind.TEXT,
    /** Emoji for [MessageKind.STICKER]. */
    val sticker: String? = null,
    /** Storage path inside the `chat-media` bucket. */
    val attachmentPath: String? = null,
    val attachmentName: String? = null,
    val attachmentMime: String? = null,
    val attachmentSize: Long? = null,
    /** Voice length, in milliseconds. */
    val durationMs: Int? = null,
    val editedAt: Long? = null,
    val deletedAt: Long? = null,
) {
    val isPending: Boolean get() = status == MessageStatus.PENDING
    val isFailed: Boolean get() = status == MessageStatus.FAILED
    val isRead: Boolean get() = readAt != null
    val isDeleted: Boolean get() = deletedAt != null
    val isEdited: Boolean get() = editedAt != null
    val hasAttachment: Boolean get() = !attachmentPath.isNullOrBlank()

    /** Short description used by notifications and transcripts. */
    val preview: String
        get() = when {
            isDeleted -> "Tin nhắn đã được thu hồi"
            kind == MessageKind.STICKER -> sticker ?: body
            kind == MessageKind.IMAGE -> "📷 Ảnh"
            kind == MessageKind.VOICE -> "🎤 Tin nhắn thoại"
            kind == MessageKind.FILE -> "📎 ${attachmentName ?: "Tệp"}"
            else -> body
        }
}

object MessageStatus {
    const val PENDING = "pending"
    const val SENT = "sent"
    const val FAILED = "failed"
}

object MessageKind {
    const val TEXT = "text"
    const val STICKER = "sticker"
    const val IMAGE = "image"
    const val FILE = "file"
    const val VOICE = "voice"
}
