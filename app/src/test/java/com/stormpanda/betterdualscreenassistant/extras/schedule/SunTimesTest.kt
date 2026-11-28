// SPDX-License-Identifier: GPL-3.0-or-later
package com.stormpanda.betterdualscreenassistant.extras.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

class SunTimesTest {
    private val london = ZoneId.of("Europe/London")

    private fun assertNear(expected: String, actualMinute: Int, toleranceMin: Int = 3) {
        val (h, m) = expected.split(":").map { it.toInt() }
        val diff = abs(h * 60 + m - actualMinute)
        assertTrue("expected ~$expected, got ${ScheduleMath.format(actualMinute)}", diff <= toleranceMin)
    }

    @Test
    fun londonMidsummerInBritishSummerTime() {
        val sun = SunTimes.compute(LocalDate.of(2026, 6, 21), 51.5074, -0.1278, london)!!
        assertNear("04:43", sun.sunriseMinute)
        assertNear("21:21", sun.sunsetMinute)
    }

    @Test
    fun londonMidwinterInGmt() {
        val sun = SunTimes.compute(LocalDate.of(2026, 12, 21), 51.5074, -0.1278, london)!!
        assertNear("08:04", sun.sunriseMinute)
        assertNear("15:54", sun.sunsetMinute)
    }

    @Test
    fun newYorkWesternLongitude() {
        val sun = SunTimes.compute(LocalDate.of(2026, 6, 21), 40.7128, -74.0060, ZoneId.of("America/New_York"))!!
        assertNear("05:25", sun.sunriseMinute)
        assertNear("20:31", sun.sunsetMinute)
    }

    @Test
    fun polarNightHasNoSunTimes() {
        assertNull(SunTimes.compute(LocalDate.of(2026, 12, 21), 78.22, 15.65, ZoneId.of("Arctic/Longyearbyen")))
    }

    @Test
    fun followSunUsesSunsetToSunriseOnlyWhenKnown() {
        val cfg = ScheduleConfig(enabled = true, startMinute = 21 * 60, endMinute = 7 * 60, followSun = true)
        val sun = SunTimes(sunriseMinute = 8 * 60, sunsetMinute = 16 * 60)
        assertEquals(16 * 60 to 8 * 60, DisplaySchedules.effectiveWindow(cfg, sun))
        assertEquals(21 * 60 to 7 * 60, DisplaySchedules.effectiveWindow(cfg, null))
        assertEquals(21 * 60 to 7 * 60, DisplaySchedules.effectiveWindow(cfg.copy(followSun = false), sun))
        val (start, end) = DisplaySchedules.effectiveWindow(cfg, sun)
        assertTrue(ScheduleMath.isActive(17 * 60, start, end))
        assertTrue(!ScheduleMath.isActive(12 * 60, start, end))
    }
}
