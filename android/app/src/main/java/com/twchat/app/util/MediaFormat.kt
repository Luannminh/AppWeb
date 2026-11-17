package com.twchat.app.util

/** Human-friendly sizes and voice lengths for the chat bubbles. */
object MediaFormat {

    fun size(bytes: Long?): String {
        val value = bytes ?: 0L
        if (value <= 0) return "0 B"
        if (value < 1024) return "$value B"
        val kb = value / 1024.0
        if (kb < 1024) return "${trim(kb)} KB"
        val mb = kb / 1024.0
        if (mb < 1024) return "${trim(mb)} MB"
        return "${trim(mb / 1024.0)} GB"
    }

    /** 7000 -> "0:07", 65000 -> "1:05". */
    fun duration(millis: Int?): String {
        val total = ((millis ?: 0) / 1000).coerceAtLeast(0)
        val minutes = total / 60
        val seconds = total % 60
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    private fun trim(value: Double): String {
        val rounded = Math.round(value * 10) / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
}
