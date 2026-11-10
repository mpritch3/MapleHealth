package com.example.medtrackv1.data.util

import java.util.Locale

/**
 * Formats elapsed counter time using fixed elapsed-duration units:
 * year = 365 days and month = 30 days.
 */
object DurationFormatter {
    fun format(durationMillis: Long): String {
        var seconds = durationMillis.coerceAtLeast(0L) / 1000L
        val years = seconds / 31_536_000L
        seconds %= 31_536_000L
        val months = seconds / 2_592_000L
        seconds %= 2_592_000L
        val weeks = seconds / 604_800L
        seconds %= 604_800L
        val days = seconds / 86_400L
        seconds %= 86_400L
        val hours = seconds / 3_600L
        val minutes = seconds % 3_600L / 60L
        val remainingSeconds = seconds % 60L
        val parts = mutableListOf<String>()
        if (years > 0) parts += "$years ${if (years == 1L) "year" else "years"}"
        if (months > 0) parts += "$months ${if (months == 1L) "month" else "months"}"
        if (weeks > 0) parts += "$weeks ${if (weeks == 1L) "week" else "weeks"}"
        if (days > 0) parts += "$days ${if (days == 1L) "day" else "days"}"
        val clock = "%02d:%02d:%02d".format(Locale.US, hours, minutes, remainingSeconds)
        if (parts.isEmpty()) return clock
        parts += clock
        return parts.joinToString(" ")
    }

    fun parse(value: String): Long {
        val input = value.trim().lowercase(Locale.ROOT)
        val clock = Regex("^(\\d+):(\\d{1,2}):(\\d{1,2})$").matchEntire(input)
        if (clock != null) {
            val (hours, minutes, seconds) = clock.destructured
            return ((hours.toLong() * 3600L + minutes.toLong() * 60L + seconds.toLong()) * 1000L)
        }
        var totalSeconds = 0L
        val matcher = Regex("(\\d+)\\s*(years?|yrs?|months?|mos?|weeks?|wks?|days?|d|hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)").findAll(input)
        if (matcher.none()) return 0L
        matcher.forEach { match ->
            val amount = match.groupValues[1].toLongOrNull() ?: return@forEach
            val unit = match.groupValues[2]
            val multiplier = when {
                unit.startsWith("y") -> 31_536_000L
                unit.startsWith("mo") -> 2_592_000L
                unit.startsWith("w") -> 604_800L
                unit.startsWith("d") -> 86_400L
                unit.startsWith("h") -> 3_600L
                unit.startsWith("m") -> 60L
                else -> 1L
            }
            totalSeconds += amount * multiplier
        }
        return totalSeconds * 1000L
    }
}