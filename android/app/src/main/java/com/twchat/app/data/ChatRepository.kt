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

    /** Local search over the cached conversation. */
    fun search(query: String): Flow<List<Message>> {
        val safe = query.replace("%", "").replace("_", "").trim()
        return dao.search("%$safe%")
    }

    suspend fun snapshot(): List<Message> = dao.getAll()

    suspend fun addPending(message: Message) = dao.upsert(message)

    suspend fun markPending(clientId: String) = dao.setStatus(clientId, MessageStatus.PENDING)

    suspend fun markFailed(clientId: String) = dao.setStatus(clientId, MessageStatus.FAILED)

    suspend fun confirmSent(clientId: String, remote: RemoteMessage) {
        dao.upsert(remote.toEntity(clientId))
    }

    suspend fun store(remote: RemoteMessage) = dao.upsert(remote.toEntity())

    suspend fun storeAll(remotes: List<RemoteMessage>) {
        if (remotes.isNotEmpty()) dao.upsertAll(remotes.map { it.toEntity() })
    }

    /** Optimistic edit: applied locally first, confirmed by the Realtime echo. */
    suspend fun updateBody(clientId: String, body: String, editedAt: Long) =
        dao.updateBody(clientId, body, editedAt)

    suspend fun softDelete(clientId: String, deletedAt: Long) = dao.softDelete(clientId, deletedAt)

    suspend fun applyReadReceipt(myId: String, upToServerId: Long, readAt: Long) =
        dao.applyReadReceipt(myId, upToServerId, readAt)

    suspend fun clear() = dao.clear()
}

private fun RemoteMessage.toEntity(existingClientId: String? = null): Message = Message(
    clientId = existingClientId ?: clientId.ifBlank { "srv-$id" },
    serverId = id,
    senderId = senderId,
    body = body,
    sentAt = createdAt,
    readAt = readAt,
    status = MessageStatus.SENT,
    kind = kind,
    sticker = sticker,
    attachmentPath = attachmentPath,
    attachmentName = attachmentName,
    attachmentMime = attachmentMime,
    attachmentSize = attachmentSize,
    durationMs = durationMs,
    editedAt = editedAt,
    deletedAt = deletedAt,
)
