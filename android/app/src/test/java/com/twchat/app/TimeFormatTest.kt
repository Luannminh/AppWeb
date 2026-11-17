package com.twchat.app

import com.twchat.app.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TimeFormatTest {

    private val zone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    @Test
    fun `today and yesterday labels`() {
        val today = LocalDate.of(2024, 6, 2)
        assertEquals("Hôm nay", TimeFormat.dayLabel(today, today))
        assertEquals("Hôm qua", TimeFormat.dayLabel(today.minusDays(1), today))
    }

    @Test
    fun `older days in the same year`() {
        val today = LocalDate.of(2024, 6, 20)
        assertEquals("Ngày 3 tháng 6", TimeFormat.dayLabel(LocalDate.of(2024, 6, 3), today))
    }

    @Test
    fun `days from another year use the full date`() {
        val today = LocalDate.of(2024, 6, 20)
        assertEquals("03/06/2023", TimeFormat.dayLabel(LocalDate.of(2023, 6, 3), today))
    }

    @Test
    fun `time is zero padded`() {
        val millis = LocalDateTime.of(2024, 6, 2, 9, 5).atZone(zone).toInstant().toEpochMilli()
        assertEquals("09:05", TimeFormat.time(millis, zone))
    }
}
