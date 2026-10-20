package com.fuelgo.app.domain

import java.time.ZonedDateTime

/**
 * A parsed weekly opening-hours schedule.
 *
 * Intervals are expressed in minutes since Monday 00:00 local time. An interval
 * may extend past a week's end (endMinute > WEEK_MINUTES) to represent overnight
 * spillover into the next week (e.g. Sunday 22:00 - Monday 02:00).
 */
data class WeeklyHours(val intervals: List<OpenInterval>) {

    fun isOpenAt(at: ZonedDateTime): Boolean {
        if (intervals.isEmpty()) return false
        // Monday = 0 ... Sunday = 6, matching OSM's week convention.
        val dayIndex = (at.dayOfWeek.value - 1)
        val minuteOfWeek = dayIndex * MINUTES_PER_DAY + at.minute + at.hour * 60
        return intervals.any { iv ->
            minuteOfWeek >= iv.startMinute && minuteOfWeek < iv.endMinute ||
                minuteOfWeek + WEEK_MINUTES >= iv.startMinute && minuteOfWeek + WEEK_MINUTES < iv.endMinute
        }
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        const val WEEK_MINUTES = 7 * MINUTES_PER_DAY

        /** Always open (24/7). */
        val ALWAYS = WeeklyHours(listOf(OpenInterval(0, WEEK_MINUTES)))
    }
}

/** Open from [startMinute] (inclusive) to [endMinute] (exclusive). */
data class OpenInterval(val startMinute: Int, val endMinute: Int)
