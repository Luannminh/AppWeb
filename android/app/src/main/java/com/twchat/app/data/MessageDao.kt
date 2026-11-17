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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: Message)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<Message>)

    @Query("UPDATE messages SET status = :status WHERE clientId = :clientId")
    suspend fun setStatus(clientId: String, status: String)

    /** Marks my messages as read by the partner, up to a given server id. */
    @Query(
        "UPDATE messages SET readAt = :readAt " +
            "WHERE senderId = :myId AND serverId IS NOT NULL AND serverId <= :upToServerId AND readAt IS NULL",
    )
    suspend fun applyReadReceipt(myId: Long, upToServerId: Long, readAt: Long)

    @Query("DELETE FROM messages")
    suspend fun clear()
}
