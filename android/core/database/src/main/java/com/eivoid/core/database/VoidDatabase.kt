package com.eivoid.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The app database.
 *
 * Version 2 added the "linked project" flag and the usage ledger; version 3
 * stores the outcome of a tool call so a restored card keeps its status;
 * version 4 adds the payment reminders ported from Ei Snore.
 *
 * Version 1 was never released, and older builds added tables in place, so
 * [MIGRATION_1_2] creates every table idempotently instead of assuming which
 * ones a given v1 file has.
 */
@Database(
    entities = [
        RepoEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        UsageEntryEntity::class,
        ReminderEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(ReminderConverters::class)
abstract class VoidDatabase : RoomDatabase() {
    abstract fun repoDao(): RepoDao
    abstract fun chatDao(): ChatDao
    abstract fun usageDao(): UsageDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_sessions` (
                        `id` TEXT NOT NULL,
                        `repoFullName` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_sessions_repoFullName` " +
                        "ON `chat_sessions` (`repoFullName`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_messages` (
                        `id` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `toolName` TEXT,
                        `toolCallId` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` " +
                        "ON `chat_messages` (`sessionId`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `usage_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `tier` TEXT NOT NULL,
                        `tokens` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_usage_entries_createdAt` " +
                        "ON `usage_entries` (`createdAt`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_usage_entries_tier` " +
                        "ON `usage_entries` (`tier`)",
                )
                if (!hasColumn(db, "repos", "isLinked")) {
                    db.execSQL(
                        "ALTER TABLE `repos` ADD COLUMN `isLinked` INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `toolOk` INTEGER")
            }
        }

        /**
         * Adds the payment reminders table. The column set and nullability
         * match [ReminderEntity] exactly, so Room's schema validation passes
         * after the migration.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `merchant` TEXT NOT NULL,
                        `bank` TEXT NOT NULL,
                        `amountMinor` INTEGER NOT NULL,
                        `currency` TEXT NOT NULL,
                        `dueAt` INTEGER NOT NULL,
                        `repeatRule` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `leadDays` INTEGER NOT NULL,
                        `leadDaysCustomized` INTEGER NOT NULL,
                        `isPaid` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `paidAt` INTEGER,
                        `notifiedForDueAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /** Every migration in order; the app builder can pass this array. */
        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        private fun hasColumn(
            db: SupportSQLiteDatabase,
            table: String,
            column: String,
        ): Boolean {
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex < 0) return false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex).equals(column, ignoreCase = true)) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
