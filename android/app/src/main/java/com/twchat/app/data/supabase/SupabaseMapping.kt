package com.twchat.app.data.supabase

import com.twchat.app.data.Person
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

/** Postgres rows (snake_case, ISO timestamps) -> Kotlin models. */
object SupabaseMapping {

    private const val MESSAGE_COLUMNS =
        "id,client_id,sender_id,body,created_at,read_at,kind,sticker,attachment_path," +
            "attachment_name,attachment_mime,attachment_size,duration_ms,edited_at,deleted_at"

    /** Column list for `select=…`, kept next to the parser so they cannot drift. */
    val messageSelect: String get() = MESSAGE_COLUMNS

    fun person(json: JSONObject): Person = Person(
        id = json.optString("id"),
        displayName = json.optString("display_name").ifBlank { "Người dùng" },
        color = json.optLong("color", 0xFFE0567B),
        inviteCode = json.optString("invite_code").ifBlank { null },
        conversationId = json.optString("conversation_id").ifBlank { null },
        lastSeen = isoToMillis(json.optString("last_seen")) ?: 0L,
    )

    fun message(json: JSONObject): RemoteMessage = RemoteMessage(
        id = json.optLong("id"),
        clientId = json.optString("client_id"),
        senderId = json.optString("sender_id"),
        body = json.optString("body"),
        createdAt = isoToMillis(json.optString("created_at")) ?: System.currentTimeMillis(),
        readAt = isoToMillis(json.optString("read_at")),
        kind = json.optString("kind").ifBlank { MessageKindFields.TEXT },
        sticker = json.optString("sticker").ifBlank { null },
        attachmentPath = json.optString("attachment_path").ifBlank { null },
        attachmentName = json.optString("attachment_name").ifBlank { null },
        attachmentMime = json.optString("attachment_mime").ifBlank { null },
        attachmentSize = json.optLong("attachment_size").takeIf { it > 0 },
        durationMs = json.optInt("duration_ms", 0).takeIf { it > 0 },
        editedAt = isoToMillis(json.optString("edited_at")),
        deletedAt = isoToMillis(json.optString("deleted_at")),
    )

    /** `2024-06-01T12:00:00.123456+00:00` (or `Z`) -> epoch millis. */
    fun isoToMillis(text: String?): Long? {
        val value = text?.trim().orEmpty()
        if (value.isEmpty() || value == "null") return null
        return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(value).toEpochMilli() }
            .getOrNull()
    }

    fun millisToIso(millis: Long): String = Instant.ofEpochMilli(millis).toString()
}
