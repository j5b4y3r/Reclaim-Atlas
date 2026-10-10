package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.BlockCategory
import com.example.data.model.BlockRule
import com.example.data.model.RuleType
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockRuleDao {
    @Query("SELECT * FROM block_rules ORDER BY priority DESC, id DESC")
    fun getAllRules(): Flow<List<BlockRule>>

    @Query("SELECT * FROM block_rules WHERE enabled = 1")
    fun getEnabledRules(): Flow<List<BlockRule>>

    @Query("SELECT * FROM block_rules WHERE enabled = 1")
    suspend fun getEnabledRulesSync(): List<BlockRule>

    @Query("SELECT * FROM block_rules WHERE category = :category")
    fun getRulesByCategory(category: BlockCategory): Flow<List<BlockRule>>

    @Query("SELECT * FROM block_rules WHERE type = :type")
    fun getRulesByType(type: RuleType): Flow<List<BlockRule>>

    @Query("SELECT * FROM block_rules WHERE id = :id")
    suspend fun getRuleById(id: Long): BlockRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: BlockRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<BlockRule>)

    @Update
    suspend fun updateRule(rule: BlockRule)

    @Delete
    suspend fun deleteRule(rule: BlockRule)

    @Query("DELETE FROM block_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)

    @Query("UPDATE block_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setRuleEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE block_rules SET enabled = :enabled WHERE category = :category")
    suspend fun setCategoryEnabled(category: BlockCategory, enabled: Boolean)

    @Query("SELECT COUNT(*) FROM block_rules WHERE enabled = 1")
    fun getEnabledRulesCount(): Flow<Int>

    @Query("DELETE FROM block_rules")
    suspend fun clearAll()
}
