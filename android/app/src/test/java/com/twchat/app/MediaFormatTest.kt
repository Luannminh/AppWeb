package com.twchat.app

import com.twchat.app.util.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFormatTest {

    @Test
    fun `sizes are human readable`() {
        assertEquals("0 B", MediaFormat.size(0))
        assertEquals("0 B", MediaFormat.size(null))
        assertEquals("512 B", MediaFormat.size(512))
        assertEquals("1 KB", MediaFormat.size(1024))
        assertEquals("1.5 KB", MediaFormat.size(1536))
        assertEquals("2 MB", MediaFormat.size(2L * 1024 * 1024))
    }

    @Test
    fun `voice durations use m ss`() {
        assertEquals("0:00", MediaFormat.duration(null))
        assertEquals("0:07", MediaFormat.duration(7_000))
        assertEquals("1:05", MediaFormat.duration(65_000))
        assertEquals("12:00", MediaFormat.duration(720_000))
    }
}
