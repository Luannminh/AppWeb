package com.twchat.app.ui.theme

/** Accent colours a person can pick for their bubbles and avatar. */
data class AccentChoice(val label: String, val argb: Long)

val AccentPalette: List<AccentChoice> = listOf(
    AccentChoice("Hồng", 0xFFE0567B),
    AccentChoice("San hô", 0xFFE4794F),
    AccentChoice("Tím", 0xFF8A6BD8),
    AccentChoice("Biển", 0xFF3A82C4),
    AccentChoice("Ngọc", 0xFF2F9E86),
    AccentChoice("Nắng", 0xFFD99A1F),
)
