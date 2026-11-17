package com.twchat.app.data

/**
 * A person known to this device: the account holder or their partner.
 * Mirrors the `user` object returned by the TwoChat server.
 */
data class Person(
    val id: Long,
    val username: String = "",
    val displayName: String,
    val color: Long,
    val inviteCode: String? = null,
    val online: Boolean = false,
    val lastSeen: Long = 0L,
) {
    val initial: String
        get() = displayName.trim().take(1).uppercase().ifEmpty { "?" }
}
