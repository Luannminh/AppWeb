package com.twchat.app.util

/** "Đang hoạt động", "Hoạt động 5 phút trước", … for the chat header subtitle. */
object RelativeTime {

    fun lastSeen(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        if (epochMillis <= 0L) return "Ngoại tuyến"
        val minutes = (now - epochMillis) / 60_000
        return when {
            minutes < 1 -> "Vừa mới truy cập"
            minutes < 60 -> "Hoạt động $minutes phút trước"
            minutes < 24 * 60 -> "Hoạt động ${minutes / 60} giờ trước"
            else -> "Hoạt động ${minutes / (24 * 60)} ngày trước"
        }
    }
}
