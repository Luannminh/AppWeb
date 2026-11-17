package com.twchat.app.data.supabase

/** Supabase auth tokens for one signed-in user. */
data class TokenSet(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String,
    val email: String?,
    val isAnonymous: Boolean,
) {
    val needsRefresh: Boolean get() = expiresAt > 0 && expiresAt - System.currentTimeMillis() < 60_000
}

sealed interface AuthResult {
    data class LoggedIn(val tokens: TokenSet) : AuthResult

    /** Email sign-up with "Confirm email" enabled: nothing to store yet. */
    data object ConfirmEmail : AuthResult
}

/** One row of `public.messages`. */
data class RemoteMessage(
    val id: Long,
    val clientId: String,
    val senderId: String,
    val body: String,
    val createdAt: Long,
    val readAt: Long?,
)

/** Anything Supabase answers with a non-2xx status. */
class SupabaseException(val status: Int, override val message: String) : Exception(message)
