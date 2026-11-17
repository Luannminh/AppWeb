package com.twchat.app.data

/**
 * Local copy of everything needed to be "logged in": Supabase project settings,
 * the auth tokens and the two profiles so the UI can render before the network
 * answers.
 */
data class AppSession(
    val serverUrl: String = "",
    val anonKey: String = "",
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAt: Long = 0L,
    val userId: String? = null,
    val email: String? = null,
    val isAnonymous: Boolean = false,
    val me: Person? = null,
    val partner: Person? = null,
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank() && anonKey.isNotBlank()
    val isLoggedIn: Boolean get() = !accessToken.isNullOrBlank() && !userId.isNullOrBlank()
    val isPaired: Boolean get() = partner != null
}
