package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_schedules")
data class FocusSchedule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val startTime: String, // HH:mm or "hh:mm a"
    val endTime: String, // HH:mm or "hh:mm a"
    val isDaily: Boolean = true,
    val selectedDays: String = "", // Comma-separated day abbreviations e.g. "Sat,Sun"
    val allowedApps: String = "", // Comma separated package names
    val isEnabled: Boolean = true
)
