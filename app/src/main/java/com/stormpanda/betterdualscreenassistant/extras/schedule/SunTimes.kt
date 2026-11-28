// SPDX-License-Identifier: GPL-3.0-or-later
// Fork-only addition (bee-san/better-dual-screen-assistant). Not part of upstream BDSA.
package com.stormpanda.betterdualscreenassistant.extras.schedule

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Today's sunrise and sunset as minutes after local midnight. */
data class SunTimes(val sunriseMinute: Int, val sunsetMinute: Int) {
    companion object {
        /** Sun's centre 0.833° below the horizon: standard refraction plus the solar disc's radius. */
        private const val ZENITH_DEG = 90.833

        /**
         * NOAA's general solar position approximation (accurate to a minute or two away from the poles).
         * Returns null when the sun doesn't rise or set that day (polar night or midnight sun).
         */
        fun compute(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): SunTimes? {
            val gamma = 2 * Math.PI / 365.0 * (date.dayOfYear - 1)
            val eqTimeMin = 229.18 * (
                0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
                    0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma)
                )
            val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
                0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
                0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
            val lat = Math.toRadians(latitude)
            val cosHa = cos(Math.toRadians(ZENITH_DEG)) / (cos(lat) * cos(decl)) - tan(lat) * tan(decl)
            if (cosHa !in -1.0..1.0) return null
            val haDeg = Math.toDegrees(acos(cosHa))
            val offsetMin = zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 60.0
            val sunrise = 720 - 4 * (longitude + haDeg) - eqTimeMin + offsetMin
            val sunset = 720 - 4 * (longitude - haDeg) - eqTimeMin + offsetMin
            return SunTimes(
                sunriseMinute = ScheduleMath.wrap(Math.round(sunrise).toInt()),
                sunsetMinute = ScheduleMath.wrap(Math.round(sunset).toInt())
            )
        }
    }
}
