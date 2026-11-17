package com.twchat.app

import com.twchat.app.data.Message
import com.twchat.app.data.MessageStatus
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

    private fun message(index: Int, senderId: Long, body: String, sentAt: Long) = Message(
        clientId = "c$index",
        serverId = index.toLong(),
        senderId = senderId,
        body = body,
        sentAt = sentAt,
        status = MessageStatus.SENT,
    )

    @Test
    fun `empty conversation has no rows`() {
        assertTrue(ChatItems.build(emptyList(), zone).isEmpty())
    }

    @Test
    fun `one header per day, oldest first`() {
        val messages = listOf(
            message(1, senderId = 10, body = "chào", sentAt = millis(2024, 6, 1, 9, 0)),
            message(2, senderId = 20, body = "hi", sentAt = millis(2024, 6, 1, 10, 30)),
            message(3, senderId = 10, body = "ngủ ngon", sentAt = millis(2024, 6, 2, 23, 0)),
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
            message(2, senderId = 20, body = "sau", sentAt = millis(2024, 6, 1, 12, 0)),
            message(1, senderId = 10, body = "trước", sentAt = millis(2024, 6, 1, 8, 0)),
        )

        val items = ChatItems.build(messages, zone, today = LocalDate.of(2024, 6, 1))

        assertEquals(3, items.size)
        assertEquals("Hôm nay", (items[0] as ChatItem.DayHeader).label)
        assertEquals("trước", (items[1] as ChatItem.Bubble).message.body)
        assertEquals("sau", (items[2] as ChatItem.Bubble).message.body)
    }

    @Test
    fun `pending message is still rendered`() {
        val pending = Message(
            clientId = "tmp-1",
            serverId = null,
            senderId = 10,
            body = "đang gửi",
            sentAt = millis(2024, 6, 1, 9, 0),
            status = MessageStatus.PENDING,
        )

        val items = ChatItems.build(listOf(pending), zone, today = LocalDate.of(2024, 6, 1))

        assertEquals(2, items.size)
        assertTrue((items[1] as ChatItem.Bubble).message.isPending)
    }
}
