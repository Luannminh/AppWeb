package com.twchat.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Small, dependency-free formatting helpers (java.time is available on API 26+). */
object TimeFormat {

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale("vi", "VN"))
    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val DATE_TIME: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("vi", "VN"))

    fun time(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(TIME)

    fun dateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(DATE_TIME)

    fun day(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /** "Hôm nay", "Hôm qua", "Ngày 3 tháng 6" or "03/06/2023". */
    fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when {
        date == today -> "Hôm nay"
        date == today.minusDays(1) -> "Hôm qua"
        date.year == today.year -> "Ngày ${date.dayOfMonth} tháng ${date.monthValue}"
        else -> date.format(DATE)
    }
}
