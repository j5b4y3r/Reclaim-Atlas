package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long = 0,
    val targetMinutes: Int,
    val completedMinutes: Int = 0,
    val isCompleted: Boolean = false,
    val blockedAttemptsCount: Int = 0,
    val sessionNotes: String = ""
)
