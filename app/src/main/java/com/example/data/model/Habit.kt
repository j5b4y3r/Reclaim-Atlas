package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val targetDuration: String = "10 min",
    val isCompletedToday: Boolean = false,
    val streak: Int = 0,
    val createdTimestamp: Long = System.currentTimeMillis()
)
