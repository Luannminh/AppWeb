package com.twchat.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages ORDER BY sentAt ASC, id ASC")
    fun observeAll(): Flow<List<Message>>

    @Query("SELECT * FROM messages ORDER BY sentAt ASC, id ASC")
    suspend fun getAll(): List<Message>

    @Insert
    suspend fun insert(message: Message): Long

    @Query("UPDATE messages SET reaction = :reaction WHERE id = :id")
    suspend fun updateReaction(id: Long, reaction: String?)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
