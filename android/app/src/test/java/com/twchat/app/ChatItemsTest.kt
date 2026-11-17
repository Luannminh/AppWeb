package com.twchat.app

import com.twchat.app.data.Message
import com.twchat.app.data.Sender
import com.twchat.app.ui.ChatItem
import com.twchat.app.ui.ChatItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ChatItemsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `empty conversation has no rows`() {
        assertTrue(ChatItems.build(emptyList(), zone).isEmpty())
    }

    @Test
    fun `one header per day, oldest first`() {
        val messages = listOf(
            Message(id = 1, sender = Sender.ME, body = "chào", sentAt = millis(2024, 6, 1, 9, 0)),
            Message(id = 2, sender = Sender.PARTNER, body = "hi", sentAt = millis(2024, 6, 1, 10, 30)),
            Message(id = 3, sender = Sender.ME, body = "ngủ ngon", sentAt = millis(2024, 6, 2, 23, 0)),
        )

        val items = ChatItems.build(messages, zone, today = LocalDate.of(2024, 6, 2))

        assertEquals(5, items.size)
        assertTrue(items[0] is ChatItem.DayHeader)
        assertEquals("Hôm qua", (items[0] as ChatItem.DayHeader).label)
        assertTrue(items[3] is ChatItem.DayHeader)
        assertEquals("Hôm nay", (items[3] as ChatItem.DayHeader).label)
        assertEquals("ngủ ngon", (items[4] as ChatItem.Bubble).message.body)
    }

    @Test
    fun `out of order input is sorted before grouping`() {
        val messages = listOf(
            Message(id = 2, sender = Sender.PARTNER, body = "sau", sentAt = millis(2024, 6, 1, 12, 0)),
            Message(id = 1, sender = Sender.ME, body = "trước", sentAt = millis(2024, 6, 1, 8, 0)),
        )

        val items = ChatItems.build(messages, zone, today = LocalDate.of(2024, 6, 1))

        assertEquals(3, items.size)
        assertEquals("Hôm nay", (items[0] as ChatItem.DayHeader).label)
        assertEquals("trước", (items[1] as ChatItem.Bubble).message.body)
        assertEquals("sau", (items[2] as ChatItem.Bubble).message.body)
    }
}
