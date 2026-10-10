package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.BlockAttempt
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockAttemptDao {
    @Query("SELECT * FROM block_attempts ORDER BY timestamp DESC")
    fun getAllAttempts(): Flow<List<BlockAttempt>>

    @Query("SELECT * FROM block_attempts WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getAttemptsSince(sinceTimestamp: Long): Flow<List<BlockAttempt>>

    @Query("SELECT COUNT(*) FROM block_attempts WHERE timestamp >= :sinceTimestamp")
    fun getAttemptsCountSince(sinceTimestamp: Long): Flow<Int>

    @Query("SELECT targetName, COUNT(*) as count FROM block_attempts GROUP BY targetName ORDER BY count DESC LIMIT :limit")
    fun getTopDistractions(limit: Int = 5): Flow<List<DistractionCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: BlockAttempt): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempts(attempts: List<BlockAttempt>)

    @Query("DELETE FROM block_attempts")
    suspend fun clearAll()
}

data class DistractionCount(
    val targetName: String,
    val count: Int
)
