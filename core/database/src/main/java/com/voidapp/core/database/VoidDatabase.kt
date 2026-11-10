package com.voidapp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The app database. The SQLCipher-backed factory is supplied by the Hilt
 * module in Task 5; tests build an in-memory instance.
 */
@Database(entities = [RepoEntity::class], version = 1, exportSchema = false)
abstract class VoidDatabase : RoomDatabase() {
    abstract fun repoDao(): RepoDao
}
