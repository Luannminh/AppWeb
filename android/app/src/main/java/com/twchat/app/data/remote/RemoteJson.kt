package com.twchat.app.data.remote

import org.json.JSONArray
import org.json.JSONObject

/** Shared JSON parsing for both the REST client and the WebSocket client. */
object RemoteJson {

    fun message(json: JSONObject): RemoteMessage = RemoteMessage(
        id = json.optLong("id"),
        clientId = json.optString("clientId"),
        senderId = json.optLong("senderId"),
        body = json.optString("body"),
        createdAt = json.optLong("createdAt"),
        readAt = if (json.isNull("readAt")) null else json.optLong("readAt"),
    )

    fun messages(array: JSONArray?): List<RemoteMessage> {
        if (array == null) return emptyList()
        val rows = ArrayList<RemoteMessage>(array.length())
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let { rows += message(it) }
        }
        return rows
    }
}
