package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.AdultDomainEntity

@Dao
interface AdultDomainDao {
    @Query("SELECT domain FROM adult_domains")
    suspend fun getAllAdultDomains(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM adult_domains WHERE domain = :domain LIMIT 1)")
    suspend fun isAdultDomain(domain: String): Boolean

    @Query("SELECT COUNT(*) FROM adult_domains")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDomains(domains: List<AdultDomainEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDomain(domain: AdultDomainEntity)
}
