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
) {
    val isPending: Boolean get() = status == MessageStatus.PENDING
    val isFailed: Boolean get() = status == MessageStatus.FAILED
    val isRead: Boolean get() = readAt != null
}

object MessageStatus {
    const val PENDING = "pending"
    const val SENT = "sent"
    const val FAILED = "failed"
}
