// SPDX-License-Identifier: GPL-3.0-or-later
package com.stormpanda.betterdualscreenassistant.extras.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
