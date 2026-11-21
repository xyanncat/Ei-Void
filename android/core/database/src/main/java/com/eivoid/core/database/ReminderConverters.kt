package com.eivoid.core.database

import androidx.room.TypeConverter

/**
 * Stores the reminder enums as their names, so a migration to a new value is
 * readable in the database and an unknown name falls back safely.
 */
class ReminderConverters {

    @TypeConverter
    fun repeatRuleToString(value: RepeatRule): String = value.name

    @TypeConverter
    fun stringToRepeatRule(value: String): RepeatRule =
        runCatching { RepeatRule.valueOf(value) }.getOrDefault(RepeatRule.NONE)

    @TypeConverter
    fun categoryToString(value: Category): String = value.name

    @TypeConverter
    fun stringToCategory(value: String): Category =
        runCatching { Category.valueOf(value) }.getOrDefault(Category.OTHER)
}
