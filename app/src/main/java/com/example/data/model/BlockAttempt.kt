package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "block_attempts")
data class BlockAttempt(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val targetName: String, // e.g. "YouTube Shorts" or "example-adult.com"
    val ruleType: RuleType,
    val category: BlockCategory,
    val wasOverridden: Boolean = false
)
