package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "adult_domains")
data class AdultDomainEntity(
    @PrimaryKey
    val domain: String,
    val source: String = "local_db",
    val addedAt: Long = System.currentTimeMillis()
)
