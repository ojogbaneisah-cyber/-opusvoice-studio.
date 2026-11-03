package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PronunciationRuleDao {
    @Query("SELECT * FROM pronunciation_rules ORDER BY timestamp DESC")
    fun getAllRules(): Flow<List<PronunciationRuleEntity>>

    @Query("SELECT * FROM pronunciation_rules WHERE isEnabled = 1")
    suspend fun getEnabledRules(): List<PronunciationRuleEntity>

    @Query("SELECT COUNT(*) FROM pronunciation_rules")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: PronunciationRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<PronunciationRuleEntity>)

    @Update
    suspend fun updateRule(rule: PronunciationRuleEntity)

    @Delete
    suspend fun deleteRule(rule: PronunciationRuleEntity)

    @Query("DELETE FROM pronunciation_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)
}
