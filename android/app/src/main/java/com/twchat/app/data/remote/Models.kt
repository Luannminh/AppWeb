package com.twchat.app.data.remote

import com.twchat.app.data.Person

/** A message exactly as the server reports it. */
data class RemoteMessage(
    val id: Long,
    val clientId: String,
    val senderId: Long,
    val body: String,
    val createdAt: Long,
    val readAt: Long?,
)

data class AuthResult(
    val token: String,
    val me: Person,
    val partner: Person?,
)

data class ConversationSnapshot(
    val me: Person,
    val partner: Person?,
    val messages: List<RemoteMessage>,
)

/** Thrown for anything the server answers with a non-2xx status. */
class ApiException(val status: Int, override val message: String) : Exception(message)
