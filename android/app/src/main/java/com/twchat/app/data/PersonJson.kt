package com.twchat.app.data

import org.json.JSONObject

/** Serialises [Person] for the local cache (DataStore). */
object PersonJson {

    fun toJson(person: Person): String = JSONObject()
        .put("id", person.id)
        .put("display_name", person.displayName)
        .put("color", person.color)
        .put("invite_code", person.inviteCode)
        .put("conversation_id", person.conversationId)
        .put("last_seen", person.lastSeen)
        .put("online", person.online)
        .toString()

    fun parse(raw: String?): Person? {
        if (raw.isNullOrBlank()) return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val id = json.optString("id")
        if (id.isBlank()) return null
        return Person(
            id = id,
            displayName = json.optString("display_name").ifBlank { "Người dùng" },
            color = json.optLong("color", 0xFFE0567B),
            inviteCode = json.optString("invite_code").ifBlank { null },
            conversationId = json.optString("conversation_id").ifBlank { null },
            lastSeen = json.optLong("last_seen"),
            online = json.optBoolean("online"),
        )
    }
}
