// SPDX-License-Identifier: GPL-3.0-or-later
package com.stormpanda.betterdualscreenassistant.extras.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleMathTest {
    @Test
    fun sameDayWindow() {
        assertTrue(ScheduleMath.isActive(10 * 60, 9 * 60, 17 * 60))
        assertFalse(ScheduleMath.isActive(17 * 60, 9 * 60, 17 * 60))
        assertFalse(ScheduleMath.isActive(8 * 60 + 59, 9 * 60, 17 * 60))
    }

    @Test
    fun overnightWindow() {
        val start = 21 * 60
        val end = 7 * 60
        assertTrue(ScheduleMath.isActive(23 * 60, start, end))
        assertTrue(ScheduleMath.isActive(0, start, end))
        assertTrue(ScheduleMath.isActive(6 * 60 + 59, start, end))
        assertFalse(ScheduleMath.isActive(7 * 60, start, end))
        assertFalse(ScheduleMath.isActive(20 * 60 + 59, start, end))
    }

    @Test
    fun emptyWindowNeverActive() {
        assertFalse(ScheduleMath.isActive(5, 5, 5))
    }

    @Test
    fun wrapAndFormat() {
        assertEquals(23 * 60 + 45, ScheduleMath.wrap(-15))
        assertEquals(0, ScheduleMath.wrap(24 * 60))
        assertEquals("07:05", ScheduleMath.format(7 * 60 + 5))
    }

    private val sunset = 15 * 60 + 59
    private val sunrise = 7 * 60 + 39

    @Test
    fun sleepsUntilTheNextSwitchNotTheNextMinute() {
        // 15:50:20 with a 15:59 to 07:39 window: wake at 15:59:00.
        val ms = ScheduleMath.nextCheckDelayMs(15 * 60 + 50, 20_000L, listOf(sunset to sunrise), false)
        assertEquals(9 * 60_000L - 20_000L, ms)
    }

    @Test
    fun longGapsAreCappedSoClockChangesSelfCorrect() {
        val ms = ScheduleMath.nextCheckDelayMs(9 * 60, 0L, listOf(sunset to sunrise), false)
        assertEquals(ScheduleMath.MAX_WAIT_MS, ms)
    }

    @Test
    fun boundaryJustAppliedPlansTheOtherEdge() {
        // At 07:39:00.2 the window just closed; the next change is sunset, beyond the cap.
        val ms = ScheduleMath.nextCheckDelayMs(sunrise, 200L, listOf(sunset to sunrise), false)
        assertEquals(ScheduleMath.MAX_WAIT_MS, ms)
        // Two schedules: the nearer edge of either wins.
        val near = ScheduleMath.nextCheckDelayMs(sunrise, 0L, listOf(sunset to sunrise, 21 * 60 to 7 * 60 + 45), false)
        assertEquals(6 * 60_000L, near)
    }

    @Test
    fun midnightIsACheckWhenFollowingTheSun() {
        val ms = ScheduleMath.nextCheckDelayMs(23 * 60 + 50, 0L, listOf(sunset to sunrise), true)
        assertEquals(10 * 60_000L, ms)
    }

    @Test
    fun earlyWakeNeverSpins() {
        // Timer fired 100 ms before the boundary minute: wait the remainder, but at least a second.
        val ms = ScheduleMath.nextCheckDelayMs(sunset - 1, 59_900L, listOf(sunset to sunrise), false)
        assertEquals(1_000L, ms)
    }

    @Test
    fun nothingToSwitchMeansNoTimer() {
        assertNull(ScheduleMath.nextCheckDelayMs(600, 0L, emptyList(), false))
        assertNull(ScheduleMath.nextCheckDelayMs(600, 0L, listOf(300 to 300), false))
    }
}
