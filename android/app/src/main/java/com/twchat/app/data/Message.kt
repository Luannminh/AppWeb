package com.twchat.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A message in the local cache. The server is the source of truth; this table
 * exists so the conversation renders instantly, works offline and survives a
 * restart. [clientId] is the stable identity of a message (generated on the
 * sending device), which is also what makes retries idempotent.
 */
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey
    val clientId: String,
    /** Server id, null while the message is still pending. */
    val serverId: Long? = null,
    val senderId: Long,
    val body: String,
    /** Epoch millis, taken from the server once acknowledged. */
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
