package com.twchat.app.ui

import com.twchat.app.data.Message
import com.twchat.app.util.TimeFormat
import java.time.LocalDate
import java.time.ZoneId

/** A row in the chat list: either a day separator or a message bubble. */
sealed interface ChatItem {
    val key: String

    data class DayHeader(val label: String, val day: LocalDate) : ChatItem {
        override val key: String = "day-$day"
    }

    data class Bubble(val message: Message) : ChatItem {
        override val key: String = "msg-${message.clientId}"
    }
}

object ChatItems {

    /**
     * Flattens messages into list items, inserting a separator whenever the day
     * changes. Messages are always sorted oldest-first so the list reads like a
     * normal conversation.
     */
    fun build(
        messages: List<Message>,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(),
    ): List<ChatItem> {
        val items = ArrayList<ChatItem>(messages.size + 2)
        var lastDay: LocalDate? = null
        messages
            .sortedWith(compareBy({ it.sentAt }, { it.clientId }))
            .forEach { message ->
                val day = TimeFormat.day(message.sentAt, zone)
                if (day != lastDay) {
                    items += ChatItem.DayHeader(TimeFormat.dayLabel(day, today), day)
                    lastDay = day
                }
                items += ChatItem.Bubble(message)
            }
        return items
    }
}
