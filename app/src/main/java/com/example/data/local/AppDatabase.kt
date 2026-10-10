package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AdultDomainEntity
import com.example.data.model.BlockAttempt
import com.example.data.model.BlockRule
import com.example.data.model.FocusSession
import com.example.data.model.FocusSchedule
import com.example.data.model.Habit
import com.example.data.model.JournalEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        BlockRule::class,
        FocusSession::class,
        BlockAttempt::class,
        Habit::class,
        JournalEntry::class,
        FocusSchedule::class,
        AdultDomainEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun blockRuleDao(): BlockRuleDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun focusScheduleDao(): FocusScheduleDao
    abstract fun blockAttemptDao(): BlockAttemptDao
    abstract fun habitDao(): HabitDao
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun adultDomainDao(): AdultDomainDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `adult_domains` (`domain` TEXT NOT NULL, `source` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`domain`))"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "self_control_database.db"
                )
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Seed initial default blocklist rules, habits, and journal
                            CoroutineScope(Dispatchers.IO).launch {
                                val database = getInstance(context)
                                database.blockRuleDao().insertRules(DefaultBlocklistData.createInitialRules())
                                
                                // Seed initial habits from UI design
                                database.habitDao().insertHabits(
                                    listOf(
                                        Habit(title = "Morning meditation", targetDuration = "5 min", isCompletedToday = true, streak = 4),
                                        Habit(title = "Exercise", targetDuration = "20 min", isCompletedToday = false, streak = 2),
                                        Habit(title = "Read a book", targetDuration = "10 min", isCompletedToday = false, streak = 5),
                                        Habit(title = "Healthy eating", targetDuration = "1 meal", isCompletedToday = false, streak = 1),
                                        Habit(title = "No phone (morning)", targetDuration = "1 hour", isCompletedToday = false, streak = 3)
                                    )
                                )

                                // Seed initial journal entries
                                database.journalEntryDao().insertEntries(
                                    listOf(
                                        JournalEntry(
                                            dateText = "Apr 12, 2025",
                                            mood = "Good",
                                            note = "Feeling stronger. Still a bit of urge but I handled it.",
                                            timestamp = System.currentTimeMillis() - 86400000L
                                        ),
                                        JournalEntry(
                                            dateText = "Apr 10, 2025",
                                            mood = "Great",
                                            note = "Productive day. No urges.",
                                            timestamp = System.currentTimeMillis() - 86400000L * 3
                                        )
                                    )
                                )
                            }
                        }
                    }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
