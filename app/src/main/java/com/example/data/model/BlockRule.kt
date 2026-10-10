package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "block_rules")
data class BlockRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: RuleType,
    val target: String, // Domain (e.g. "tiktok.com"), package ("com.zhiliaoapp.musically"), keyword, or category key
    val category: BlockCategory,
    val enabled: Boolean = true,
    val isException: Boolean = false, // If true, whitelisted domain or app
    val priority: Int = 0, // Higher priority evaluated first
    val action: RuleAction = RuleAction.BLOCK,
    // Scheduling: comma-separated days of week (1=Sun, 2=Mon... 7=Sat) or empty for all days
    val scheduleDays: String = "", 
    // HH:mm 24-hour format e.g. "22:00" to "07:00" or empty for always active
    val scheduleStartTime: String = "",
    val scheduleEndTime: String = "",
    val dailyLimitMinutes: Int = 0, // 0 means no time limit, strictly blocked
    val createdAt: Long = System.currentTimeMillis()
)
