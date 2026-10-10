package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.FocusSchedule
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusScheduleDao {
    @Query("SELECT * FROM focus_schedules ORDER BY id DESC")
    fun getAllSchedules(): Flow<List<FocusSchedule>>

    @Query("SELECT * FROM focus_schedules")
    suspend fun getAllSchedulesSync(): List<FocusSchedule>

    @Query("SELECT * FROM focus_schedules WHERE isEnabled = 1")
    suspend fun getEnabledSchedulesSync(): List<FocusSchedule>

    @Query("UPDATE focus_schedules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setScheduleEnabled(id: Long, enabled: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: FocusSchedule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<FocusSchedule>)

    @Update
    suspend fun updateSchedule(schedule: FocusSchedule)

    @Delete
    suspend fun deleteSchedule(schedule: FocusSchedule)

    @Query("DELETE FROM focus_schedules")
    suspend fun clearAll()
}
