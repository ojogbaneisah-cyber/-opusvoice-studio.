package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceTakeDao {
    @Query("SELECT * FROM voice_takes ORDER BY timestamp DESC")
    fun getAllTakes(): Flow<List<VoiceTakeEntity>>

    @Query("SELECT * FROM voice_takes WHERE id = :takeId LIMIT 1")
    suspend fun getTakeById(takeId: Long): VoiceTakeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTake(take: VoiceTakeEntity): Long

    @Delete
    suspend fun deleteTake(take: VoiceTakeEntity)

    @Query("DELETE FROM voice_takes WHERE id = :takeId")
    suspend fun deleteTakeById(takeId: Long)
}
