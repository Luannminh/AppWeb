package com.twchat.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages ORDER BY sentAt ASC, clientId ASC")
    fun observeAll(): Flow<List<Message>>

    @Query("SELECT * FROM messages ORDER BY sentAt ASC, clientId ASC")
    suspend fun getAll(): List<Message>

    /** Local full-text-ish search over the cached conversation. */
    @Query("SELECT * FROM messages WHERE body LIKE :pattern ORDER BY sentAt ASC LIMIT 200")
    fun search(pattern: String): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: Message)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<Message>)

    @Query("UPDATE messages SET status = :status WHERE clientId = :clientId")
    suspend fun setStatus(clientId: String, status: String)

    @Query("UPDATE messages SET body = :body, editedAt = :editedAt WHERE clientId = :clientId")
    suspend fun updateBody(clientId: String, body: String, editedAt: Long)

    @Query("UPDATE messages SET deletedAt = :deletedAt WHERE clientId = :clientId")
    suspend fun softDelete(clientId: String, deletedAt: Long)

    /** Marks my messages as read by the partner, up to a given Postgres id. */
    @Query(
        "UPDATE messages SET readAt = :readAt " +
            "WHERE senderId = :myId AND serverId IS NOT NULL AND serverId <= :upToServerId AND readAt IS NULL",
    )
    suspend fun applyReadReceipt(myId: String, upToServerId: Long, readAt: Long)

    @Query("DELETE FROM messages")
    suspend fun clear()
}
