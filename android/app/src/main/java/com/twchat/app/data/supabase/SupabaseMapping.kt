package com.twchat.app.data.supabase

import com.twchat.app.data.Person
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

/** Postgres rows (snake_case, ISO timestamps) -> Kotlin models. */
object SupabaseMapping {

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
