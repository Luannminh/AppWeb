package com.twchat.app.util

import com.twchat.app.data.Message
import com.twchat.app.data.Person
import java.time.ZoneId

/** Turns the cached conversation into plain text so it can be shared or backed up. */
object Transcript {

    fun build(
        messages: List<Message>,
        me: Person?,
        partner: Person?,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis(),
    ): String = buildString {
        val myName = me?.displayName ?: "Bạn"
        val partnerName = partner?.displayName ?: "Người ấy"
        appendLine("Cuộc trò chuyện của $myName và $partnerName")
        appendLine("Xuất lúc ${TimeFormat.dateTime(now, zone)}")
        appendLine("-".repeat(24))
        appendLine()
        if (messages.isEmpty()) appendLine("(Chưa có tin nhắn nào)")
        messages.forEach { message ->
            val who = if (message.senderId == me?.id) myName else partnerName
            appendLine("[${TimeFormat.dateTime(message.sentAt, zone)}] $who: ${message.body}")
        }
    }
}
