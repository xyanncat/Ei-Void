package com.voidapp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The app database.
 *
 * The schema is still version 1 because nothing has shipped yet: tables are
 * added in place instead of through migrations. The first release freezes
 * version 1 and every later change needs a migration.
 */
@Database(
    entities = [
        RepoEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        UsageEntryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class VoidDatabase : RoomDatabase() {
    abstract fun repoDao(): RepoDao
    abstract fun chatDao(): ChatDao
    abstract fun usageDao(): UsageDao
}
