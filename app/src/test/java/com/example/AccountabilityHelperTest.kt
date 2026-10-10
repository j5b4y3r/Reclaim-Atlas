package com.example

import com.example.domain.engine.AccountabilityHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AccountabilityHelperTest {

    @Test
    fun testWindowActiveCalculations() {
        // Window starting at 12:00 PM (12:00) with 30 min duration (12:00 -> 12:30)
        val calInside = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
        }
        assertTrue(AccountabilityHelper.isWindowActive(12, 0, 30, calInside))

        // Outside before window: 11:59 AM
        val calBefore = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 0)
        }
        assertFalse(AccountabilityHelper.isWindowActive(12, 0, 30, calBefore))

        // Outside after window: 12:30 PM (end boundary is exclusive)
        val calAfter = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }
        assertFalse(AccountabilityHelper.isWindowActive(12, 0, 30, calAfter))

        // Window start boundary: 12:00 PM (inclusive)
        val calStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        assertTrue(AccountabilityHelper.isWindowActive(12, 0, 30, calStart))
    }

    @Test
    fun testMidnightSpanningWindow() {
        // Window 23:45 to 00:15 (duration 30 min)
        val calAtMidnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
        }
        assertTrue(AccountabilityHelper.isWindowActive(23, 45, 30, calAtMidnight))

        val calBeforeMidnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 50)
        }
        assertTrue(AccountabilityHelper.isWindowActive(23, 45, 30, calBeforeMidnight))

        val calOutside = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 20)
        }
        assertFalse(AccountabilityHelper.isWindowActive(23, 45, 30, calOutside))
    }

    @Test
    fun testTimeFormatting() {
        assertEquals("12:00 PM", AccountabilityHelper.formatTime12Hour(12, 0))
        assertEquals("12:05 PM", AccountabilityHelper.formatTime12Hour(12, 5))
        assertEquals("12:00 AM", AccountabilityHelper.formatTime12Hour(0, 0))
        assertEquals("11:30 PM", AccountabilityHelper.formatTime12Hour(23, 30))
        assertEquals("8:15 AM", AccountabilityHelper.formatTime12Hour(8, 15))

        assertEquals("12:00 PM – 12:30 PM", AccountabilityHelper.formatWindowRange(12, 0, 30))
        assertEquals("8:00 AM – 9:00 AM", AccountabilityHelper.formatWindowRange(8, 0, 60))
    }

    @Test
    fun testCountdownCalculations() {
        // At 10:00 AM, next window at 12:00 PM -> exactly 2 hours 0 minutes
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }
        val (h, m) = AccountabilityHelper.getTimeUntilNextWindow(12, 0, cal)
        assertEquals(2, h)
        assertEquals(0, m)
        assertEquals("2h", AccountabilityHelper.formatTimeUntil(h, m))
        assertEquals("2h 15m", AccountabilityHelper.formatTimeUntil(2, 15))
        assertEquals("45m", AccountabilityHelper.formatTimeUntil(0, 45))
    }
}
