package com.twchat.app.util

import com.twchat.app.data.Message
import com.twchat.app.data.Profile
import com.twchat.app.data.Sender
import java.time.ZoneId

/** Turns the conversation into plain text so it can be shared or backed up. */
object Transcript {

    fun build(
        messages: List<Message>,
        profile: Profile,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis(),
    ): String = buildString {
        appendLine("Cuộc trò chuyện của ${profile.meName} và ${profile.partnerName}")
        appendLine("Xuất lúc ${TimeFormat.dateTime(now, zone)}")
        appendLine("-".repeat(24))
        appendLine()
        if (messages.isEmpty()) {
            appendLine("(Chưa có tin nhắn nào)")
        }
        messages.forEach { message ->
            val who = if (message.sender == Sender.ME) profile.meName else profile.partnerName
            appendLine("[${TimeFormat.dateTime(message.sentAt, zone)}] $who: ${message.body}")
            message.reaction?.let { appendLine("    $it") }
        }
    }
}
