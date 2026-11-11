package com.voidapp.core.common.time

import java.util.Calendar

/** The first instant of the current month, in epoch milliseconds. */
fun startOfMonthMillis(now: Long = System.currentTimeMillis()): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = now
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}
