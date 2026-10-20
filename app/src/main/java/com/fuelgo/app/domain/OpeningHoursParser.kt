package com.fuelgo.app.domain

import java.time.ZonedDateTime
import com.fuelgo.app.domain.model.PumpStatus

/**
 * Parser for OSM `opening_hours` values (the subset commonly seen on fuel
 * stations), e.g.:
 *   "24/7"
 *   "Mo-Fr 08:00-20:00"
 *   "Mo-Fr 08:00-12:00,13:00-17:00; Sa 09:00-13:00"
 *   "Sa 22:00-02:00"                (overnight range)
 *   "Mo-Sa 08:00-20:00; Su off"     ("off" rules are redundant: OSM is
 *                                    closed-by-default, so they are ignored)
 *
 * Returns null when the value cannot be confidently parsed — the UI then shows
 * "hours unknown" instead of a wrong open/closed answer.
 */
object OpeningHoursParser {

    private val DAY_NAMES = mapOf(
        "mo" to 0, "tu" to 1, "we" to 2, "th" to 3, "fr" to 4, "sa" to 5, "su" to 6,
    )

    fun parse(raw: String?): WeeklyHours? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim()
        if (value.equals("24/7", ignoreCase = true)) return WeeklyHours.ALWAYS

        val intervals = mutableListOf<OpenInterval>()
        for (rule in value.split(';')) {
            val parsed = parseRule(rule.trim()) ?: return null
            intervals += parsed
        }
        if (intervals.isEmpty()) return null
        return WeeklyHours(intervals)
    }

    /** Returns null (unparsable) or the intervals contributed by this rule. */
    private fun parseRule(rule: String): List<OpenInterval>? {
        if (rule.isEmpty()) return null
        // "off"/"closed" rules are redundant under closed-by-default semantics.
        if (rule.endsWith("off", ignoreCase = true) ||
            rule.endsWith("closed", ignoreCase = true)
        ) return emptyList()

        val tokens = rule.split(' ', '\t').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        val days: Set<Int>
        val timeTokens: List<String>
        if (tokens.size == 1) {
            // Time-only rule applies to every day, e.g. "08:00-20:00".
            days = (0..6).toSet()
            timeTokens = tokens.flatMap { it.split(',') }
        } else {
            val dayToken = tokens.first().lowercase()
            days = parseDays(dayToken) ?: return null
            // Handles both "08:00-12:00,13:00-17:00" and "08:00-12:00 13:00-17:00".
            timeTokens = tokens.drop(1).flatMap { it.split(',') }
            if (timeTokens.isEmpty()) return null
        }

        val intervals = mutableListOf<OpenInterval>()
        for (timeToken in timeTokens) {
            val range = parseTimeRange(timeToken) ?: return null
            for (day in days) {
                val (startOffset, lengthMinutes) = range
                val start = day * WeeklyHours.MINUTES_PER_DAY + startOffset
                intervals += OpenInterval(start, start + lengthMinutes)
            }
        }
        return intervals
    }

    /** "Mo-Fr", "Sa,Su", "Mo" -> set of day indices (Monday = 0). */
    private fun parseDays(token: String): Set<Int>? {
        val days = mutableSetOf<Int>()
        for (part in token.split(',')) {
            if (part.isBlank()) return null
            val rangeParts = part.split('-')
            when {
                rangeParts.size == 2 -> {
                    val from = DAY_NAMES[rangeParts[0].trim()] ?: return null
                    val to = DAY_NAMES[rangeParts[1].trim()] ?: return null
                    if (to < from) return null // wrapped week ranges not supported
                    days += (from..to).toList()
                }
                else -> days += DAY_NAMES[part.trim()] ?: return null
            }
        }
        return days
    }

    /** "08:00-20:00" -> (480, 720); "22:00-02:00" -> (1320, 240). */
    private fun parseTimeRange(token: String): Pair<Int, Int>? {
        val parts = token.split('-')
        if (parts.size != 2) return null
        val start = parseTime(parts[0]) ?: return null
        val end = parseTime(parts[1]) ?: return null
        val length = if (end > start) end - start else end + WeeklyHours.MINUTES_PER_DAY - start
        if (length <= 0) return null
        return start to length
    }

    /** "08:00" -> 480; "24:00" -> 1440; "07:30+" -> 450 (treated as until midnight). */
    private fun parseTime(token: String): Int? {
        val t = token.removeSuffix("+")
        val parts = t.split(':')
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..24 || m !in 0..59) return null
        return h * 60 + m
    }
}

/**
 * Converts Google Places (new API) `regularOpeningHours` into [WeeklyHours].
 *
 * Places hours are stored in the DB as a compact raw string rendered by
 * [com.fuelgo.app.data.repo], one period per rule:
 *     "d:HHMM-d:HHMM;d:HHMM-d:HHMM"
 * where `d` is the Monday-based day index (0 = Monday) and HHMM is 24h time.
 * Example: Sunday 23:00 - Monday 02:00 => "6:2300-0:0200".
 */
object PlacesHoursParser {

    fun parse(raw: String?): WeeklyHours? {
        if (raw.isNullOrBlank()) return null
        val intervals = mutableListOf<OpenInterval>()
        for (rule in raw.split(';')) {
            if (rule.isBlank()) continue
            val sides = rule.split('-')
            if (sides.size != 2) return null
            val open = parsePoint(sides[0]) ?: return null
            val close = parsePoint(sides[1]) ?: return null
            var end = close.first * WeeklyHours.MINUTES_PER_DAY + close.second
            val start = open.first * WeeklyHours.MINUTES_PER_DAY + open.second
            if (end <= start) end += WeeklyHours.WEEK_MINUTES
            intervals += OpenInterval(start, end)
        }
        if (intervals.isEmpty()) return null
        return WeeklyHours(intervals)
    }

    /** "d:HHMM" -> (dayIndex, minutesOfDay). */
    private fun parsePoint(token: String): Pair<Int, Int>? {
        val parts = token.split(':')
        if (parts.size != 2) return null
        val day = parts[0].trim().toIntOrNull() ?: return null
        if (day !in 0..6) return null
        val t = parts[1].trim()
        if (t.length != 4) return null
        val h = t.substring(0, 2).toIntOrNull() ?: return null
        val m = t.substring(2, 4).toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return day to (h * 60 + m)
    }
}

/** Convenience: is the station open at [at] given its raw hours string? */
fun rawHoursStatus(rawOpeningHours: String?, at: ZonedDateTime): Boolean? {
    val hours = OpeningHoursParser.parse(rawOpeningHours)
        ?: PlacesHoursParser.parse(rawOpeningHours)
        ?: return null
    return hours.isOpenAt(at)
}

/** Hours-derived [PumpStatus] from a raw hours string. */
fun hoursStatus(rawOpeningHours: String?, at: ZonedDateTime): PumpStatus =
    when (rawHoursStatus(rawOpeningHours, at)) {
        true -> PumpStatus.OPEN
        false -> PumpStatus.CLOSED
        null -> PumpStatus.UNKNOWN
    }
