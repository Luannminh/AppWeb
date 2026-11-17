package com.twchat.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One message inside the two-person conversation.
 *
 * The conversation is fully local: everything lives in the Room database on this
 * device, no account and no network involved.
 */
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** [Sender.ME] or [Sender.PARTNER]. */
    val sender: String = Sender.ME,
    val body: String,
    /** Epoch millis. */
    val sentAt: Long,
    /** Optional emoji reaction, null when the message has no reaction. */
    val reaction: String? = null,
)

/** Who wrote a message. Stored as a plain string so the schema stays trivial. */
object Sender {
    const val ME = "me"
    const val PARTNER = "partner"
}
