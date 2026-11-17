package com.twchat.app.data

import com.twchat.app.data.supabase.RemoteMessage
import kotlinx.coroutines.flow.Flow

/**
 * Room cache in front of Supabase. Writing through the cache (instead of holding
 * messages in memory) is what makes the app feel like a real messenger: opening
 * it shows history instantly, pending messages survive a restart, and retries
 * reuse [Message.clientId] so Postgres never stores the same message twice.
 */
class ChatRepository(private val dao: MessageDao) {

    fun observeMessages(): Flow<List<Message>> = dao.observeAll()

    suspend fun snapshot(): List<Message> = dao.getAll()

    suspend fun addPending(clientId: String, body: String, myId: String) {
        dao.upsert(
            Message(
                clientId = clientId,
                serverId = null,
                senderId = myId,
                body = body,
                sentAt = System.currentTimeMillis(),
                status = MessageStatus.PENDING,
            ),
        )
    }

    suspend fun markPending(clientId: String) = dao.setStatus(clientId, MessageStatus.PENDING)

    suspend fun markFailed(clientId: String) = dao.setStatus(clientId, MessageStatus.FAILED)

    suspend fun confirmSent(clientId: String, remote: RemoteMessage) {
        dao.upsert(remote.toEntity().copy(clientId = clientId))
    }

    suspend fun store(remote: RemoteMessage) = dao.upsert(remote.toEntity())

    suspend fun storeAll(remotes: List<RemoteMessage>) {
        if (remotes.isNotEmpty()) dao.upsertAll(remotes.map { it.toEntity() })
    }

    suspend fun applyReadReceipt(myId: String, upToServerId: Long, readAt: Long) =
        dao.applyReadReceipt(myId, upToServerId, readAt)

    suspend fun clear() = dao.clear()
}

private fun RemoteMessage.toEntity(): Message = Message(
    clientId = clientId.ifBlank { "srv-$id" },
    serverId = id,
    senderId = senderId,
    body = body,
    sentAt = createdAt,
    readAt = readAt,
    status = MessageStatus.SENT,
)
