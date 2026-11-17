package com.twchat.app.data

import org.json.JSONObject

/** JSON <-> [Person] helpers shared by the API client and the session store. */
object PersonJson {

    fun parse(json: JSONObject?): Person? {
        if (json == null || json.isNull("id")) return null
        val username = json.optString("username")
        return Person(
            id = json.optLong("id"),
            username = username,
            displayName = json.optString("displayName").ifBlank { username },
            color = json.optLong("color"),
            inviteCode = json.optString("inviteCode").ifBlank { null },
            online = json.optBoolean("online"),
            lastSeen = json.optLong("lastSeen"),
        )
    }

    fun parse(raw: String?): Person? {
        if (raw.isNullOrBlank()) return null
        return runCatching { parse(JSONObject(raw)) }.getOrNull()
    }

    fun toJson(person: Person): String = JSONObject()
        .put("id", person.id)
        .put("username", person.username)
        .put("displayName", person.displayName)
        .put("color", person.color)
        .put("inviteCode", person.inviteCode)
        .put("online", person.online)
        .put("lastSeen", person.lastSeen)
        .toString()
}
