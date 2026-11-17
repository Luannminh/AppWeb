package com.twchat.app

import com.twchat.app.util.RelativeTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `never seen`() {
        assertEquals("Ngoại tuyến", RelativeTime.lastSeen(0L, now))
    }

    @Test
    fun `a few minutes ago`() {
        assertEquals("Hoạt động 5 phút trước", RelativeTime.lastSeen(now - 5 * 60_000, now))
    }

    @Test
    fun `hours ago`() {
        assertEquals("Hoạt động 3 giờ trước", RelativeTime.lastSeen(now - 3 * 60 * 60_000, now))
    }

    @Test
    fun `days ago`() {
        assertEquals("Hoạt động 2 ngày trước", RelativeTime.lastSeen(now - 2 * 24 * 60 * 60_000, now))
    }
}
