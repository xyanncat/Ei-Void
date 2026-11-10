package com.voidapp.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "repos")
data class RepoEntity(
    @PrimaryKey val id: String,
    val owner: String,
    val name: String,
    val fullName: String,
    // `private` is a Kotlin keyword; the column keeps the natural name.
    @ColumnInfo(name = "private") val isPrivate: Boolean,
    val defaultBranch: String,
    val description: String?,
    val updatedAt: Long,
)
