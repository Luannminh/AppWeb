package com.twchat.app.data

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the conversation. The ViewModel never touches the
 * DAO directly so the storage backend can be swapped later (for example for a
 * synced Room database) without touching the UI.
 */
class ChatRepository(private val dao: MessageDao) {

    fun observeMessages(): Flow<List<Message>> = dao.observeAll()

    suspend fun send(sender: String, body: String, sentAt: Long = System.currentTimeMillis()) {
        val text = body.trim()
        if (text.isEmpty()) return
        dao.insert(Message(sender = sender, body = text, sentAt = sentAt))
    }

    suspend fun setReaction(id: Long, reaction: String?) = dao.updateReaction(id, reaction)

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun clear() = dao.deleteAll()

    suspend fun snapshot(): List<Message> = dao.getAll()
}
