package com.example.domain.engine

import java.util.Calendar

data class LockedActionTarget(
    val actionKey: String,
    val actionTitle: String,
    val targetType: String = "SINGLE_ACTION",
    val ruleId: Long? = null,
    val payload: String = ""
)

data class AccountabilityLockStatus(
    val isLocked: Boolean,
    val windowRangeText: String = "",
    val timeUntilNextWindowText: String = "",
    val selectedTimeText: String = "",
    val remainingMinutesInWindow: Int = 0,
    val lockedTarget: LockedActionTarget? = null
)

object AccountabilityHelper {

    /**
     * Checks if current time is within the daily allowed window [startMinutes, startMinutes + durationMinutes).
     */
    fun isWindowActive(
        startHour: Int,
        startMinute: Int,
        durationMinutes: Int,
        calendar: Calendar = Calendar.getInstance()
    ): Boolean {
        val nowHour = calendar.get(Calendar.HOUR_OF_DAY)
        val nowMinute = calendar.get(Calendar.MINUTE)
        val nowMinutes = nowHour * 60 + nowMinute
        val startMinutes = startHour * 60 + startMinute
        val duration = durationMinutes.coerceAtLeast(1)

        val endMinutes = startMinutes + duration
        return if (endMinutes <= 1440) {
            nowMinutes in startMinutes until endMinutes
        } else {
            val wrappedEnd = endMinutes % 1440
            nowMinutes >= startMinutes || nowMinutes < wrappedEnd
        }
    }

    /**
     * Returns Pair(hours, minutes) until the next window start.
     */
    fun getTimeUntilNextWindow(
        startHour: Int,
        startMinute: Int,
        calendar: Calendar = Calendar.getInstance()
    ): Pair<Int, Int> {
        val nowHour = calendar.get(Calendar.HOUR_OF_DAY)
        val nowMinute = calendar.get(Calendar.MINUTE)
        val nowMinutes = nowHour * 60 + nowMinute
        val startMinutes = startHour * 60 + startMinute

        val diffMinutes = if (startMinutes >= nowMinutes) {
            startMinutes - nowMinutes
        } else {
            (1440 - nowMinutes) + startMinutes
        }
        return Pair(diffMinutes / 60, diffMinutes % 60)
    }

    /**
     * Returns a human readable string for time until next window: e.g. "2h 15m" or "45m".
     */
    fun formatTimeUntil(hours: Int, minutes: Int): String {
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "< 1m"
        }
    }

    /**
     * Returns remaining minutes in the currently open window.
     */
    fun getRemainingWindowMinutes(
        startHour: Int,
        startMinute: Int,
        durationMinutes: Int,
        calendar: Calendar = Calendar.getInstance()
    ): Int {
        val nowHour = calendar.get(Calendar.HOUR_OF_DAY)
        val nowMinute = calendar.get(Calendar.MINUTE)
        val nowMinutes = nowHour * 60 + nowMinute
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = startMinutes + durationMinutes

        return if (endMinutes <= 1440) {
            (endMinutes - nowMinutes).coerceAtLeast(0)
        } else {
            val wrappedEnd = endMinutes % 1440
            if (nowMinutes >= startMinutes) {
                (1440 - nowMinutes + wrappedEnd).coerceAtLeast(0)
            } else {
                (wrappedEnd - nowMinutes).coerceAtLeast(0)
            }
        }
    }

    /**
     * Formats hour (0-23) and minute (0-59) to 12-hour string, e.g. "12:00 PM" or "8:30 AM".
     */
    fun formatTime12Hour(hour: Int, minute: Int): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val hour12 = when (val h = hour % 12) {
            0 -> 12
            else -> h
        }
        val minStr = if (minute < 10) "0$minute" else "$minute"
        return "$hour12:$minStr $amPm"
    }

    /**
     * Formats start and duration to range string, e.g. "12:00 PM – 12:30 PM".
     */
    fun formatWindowRange(startHour: Int, startMinute: Int, durationMinutes: Int): String {
        val startStr = formatTime12Hour(startHour, startMinute)
        val totalEndMin = (startHour * 60 + startMinute + durationMinutes) % 1440
        val endHour = totalEndMin / 60
        val endMinute = totalEndMin % 60
        val endStr = formatTime12Hour(endHour, endMinute)
        return "$startStr – $endStr"
    }
}
