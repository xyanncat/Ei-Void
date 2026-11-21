package com.eivoid.core.database

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
    /** True when the user added it as one of their projects. */
    @ColumnInfo(name = "isLinked", defaultValue = "0") val isLinked: Boolean = false,
)
