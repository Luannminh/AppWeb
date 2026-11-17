package com.twchat.app.data

/**
 * The two people in the conversation: names and accent colours, plus the flag
 * that tells us whether the welcome screen has been completed.
 */
data class Profile(
    val meName: String = DEFAULT_ME_NAME,
    val partnerName: String = DEFAULT_PARTNER_NAME,
    /** ARGB colour, e.g. 0xFFE0567B. */
    val meColor: Long = DEFAULT_ME_COLOR,
    val partnerColor: Long = DEFAULT_PARTNER_COLOR,
    val onboarded: Boolean = false,
) {
    companion object {
        const val DEFAULT_ME_NAME = "Bạn"
        const val DEFAULT_PARTNER_NAME = "Người ấy"
        const val DEFAULT_ME_COLOR = 0xFFE0567B
        const val DEFAULT_PARTNER_COLOR = 0xFF2F7F86
    }
}
