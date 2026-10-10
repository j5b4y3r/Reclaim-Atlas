package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.FocusSession
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE isCompleted = 1 ORDER BY startTime DESC")
    fun getCompletedSessions(): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE startTime >= :sinceTimestamp ORDER BY startTime DESC")
    fun getSessionsSince(sinceTimestamp: Long): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): FocusSession?

    @Query("SELECT * FROM focus_sessions WHERE endTime = 0 ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSession(): FocusSession?

    @Query("SELECT * FROM focus_sessions WHERE endTime = 0 ORDER BY startTime DESC LIMIT 1")
    fun observeActiveSession(): Flow<FocusSession?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<FocusSession>)

    @Update
    suspend fun updateSession(session: FocusSession)

    @Query("DELETE FROM focus_sessions")
    suspend fun clearAll()
}
