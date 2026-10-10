package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateText: String,
    val mood: String = "Good",
    val note: String,
    val timestamp: Long = System.currentTimeMillis()
)
