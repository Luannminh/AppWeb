package com.twchat.app.data

/**
 * One of the two people in the conversation, mirroring a row of
 * `public.profiles` in Supabase.
 */
data class Person(
    /** `auth.users.id` (uuid). */
    val id: String,
    val displayName: String,
    /** ARGB colour, e.g. 0xFFE0567B. */
    val color: Long,
    /** 6-character code the other person types to pair. */
    val inviteCode: String? = null,
    val conversationId: String? = null,
    /** Epoch millis of the last time this person opened the app. */
    val lastSeen: Long = 0L,
    /** Filled in from the realtime channel, not stored in Postgres. */
    val online: Boolean = false,
) {
    val initial: String
        get() = displayName.trim().take(1).uppercase().ifEmpty { "?" }
}
