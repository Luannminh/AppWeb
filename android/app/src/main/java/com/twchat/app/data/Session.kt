package com.twchat.app.data

/**
 * Everything we remember about the user's account: which server they use, the
 * session token, and a cached copy of both people so the UI can render before
 * the first network round-trip.
 */
data class Session(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val token: String? = null,
    val me: Person? = null,
    val partner: Person? = null,
) {
    val isLoggedIn: Boolean get() = !token.isNullOrBlank() && me != null
    val isPaired: Boolean get() = partner != null

    companion object {
        /** Host loopback as seen from the Android emulator. */
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:3000"
    }
}
