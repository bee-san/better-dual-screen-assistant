// SPDX-License-Identifier: GPL-3.0-or-later
// Fork-only addition (bee-san/better-dual-screen-assistant). Not part of upstream BDSA.
package com.stormpanda.betterdualscreenassistant.extras.schedule

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import com.stormpanda.betterdualscreenassistant.privileged.PServerClient
import java.util.Locale
import java.util.concurrent.Executors

/** A place to work out sunset and sunrise for. */
data class SunPlace(val name: String, val latitude: Double, val longitude: Double)

/**
 * Where the sunset-to-sunrise schedules take their sun times from: a chosen city, or the device's approximate
 * location looked up once on request or when the time zone changes. Nothing polls or tracks location.
 */
object SunLocation {
    private const val TAG = "BDSA-SunLocation"
    private const val PREFS = "bdsa_extras_schedules"
    private const val KEY_NAME = "sun_place_name"
    private const val KEY_LAT = "sun_lat"
    private const val KEY_LON = "sun_lon"
    private const val KEY_AUTO = "sun_auto_on_timezone_change"

    val presets: List<SunPlace> = listOf(
        SunPlace("London", 51.5074, -0.1278),
        SunPlace("Edinburgh", 55.9533, -3.1883),
        SunPlace("Dublin", 53.3498, -6.2603),
        SunPlace("Lisbon", 38.7223, -9.1393),
        SunPlace("Madrid", 40.4168, -3.7038),
        SunPlace("Paris", 48.8566, 2.3522),
        SunPlace("Amsterdam", 52.3676, 4.9041),
        SunPlace("Berlin", 52.5200, 13.4050),
        SunPlace("Rome", 41.9028, 12.4964),
        SunPlace("Stockholm", 59.3293, 18.0686),
        SunPlace("Oslo", 59.9139, 10.7522),
        SunPlace("Helsinki", 60.1699, 24.9384),
        SunPlace("Athens", 37.9838, 23.7275),
        SunPlace("Istanbul", 41.0082, 28.9784),
        SunPlace("Cairo", 30.0444, 31.2357),
        SunPlace("Nairobi", -1.2921, 36.8219),
        SunPlace("Cape Town", -33.9249, 18.4241),
        SunPlace("Dubai", 25.2048, 55.2708),
        SunPlace("Mumbai", 19.0760, 72.8777),
        SunPlace("Bangkok", 13.7563, 100.5018),
        SunPlace("Singapore", 1.3521, 103.8198),
        SunPlace("Hong Kong", 22.3193, 114.1694),
        SunPlace("Shanghai", 31.2304, 121.4737),
        SunPlace("Seoul", 37.5665, 126.9780),
        SunPlace("Tokyo", 35.6762, 139.6503),
        SunPlace("Sydney", -33.8688, 151.2093),
        SunPlace("Melbourne", -37.8136, 144.9631),
        SunPlace("Auckland", -36.8485, 174.7633),
        SunPlace("Honolulu", 21.3069, -157.8583),
        SunPlace("Los Angeles", 34.0522, -118.2437),
        SunPlace("San Francisco", 37.7749, -122.4194),
        SunPlace("Vancouver", 49.2827, -123.1207),
        SunPlace("Denver", 39.7392, -104.9903),
        SunPlace("Chicago", 41.8781, -87.6298),
        SunPlace("Toronto", 43.6532, -79.3832),
        SunPlace("New York", 40.7128, -74.0060),
        SunPlace("Mexico City", 19.4326, -99.1332),
        SunPlace("São Paulo", -23.5505, -46.6333),
        SunPlace("Buenos Aires", -34.6037, -58.3816)
    )

    val DEFAULT: SunPlace = presets.first()

    fun place(context: Context): SunPlace {
        val p = prefs(context)
        if (!p.contains(KEY_LAT) || !p.contains(KEY_LON)) return DEFAULT
        return SunPlace(
            name = p.getString(KEY_NAME, null) ?: DEFAULT.name,
            latitude = java.lang.Double.longBitsToDouble(p.getLong(KEY_LAT, 0L)),
            longitude = java.lang.Double.longBitsToDouble(p.getLong(KEY_LON, 0L))
        )
    }

    fun setPlace(context: Context, place: SunPlace) {
        prefs(context).edit()
            .putString(KEY_NAME, place.name)
            .putLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(place.latitude))
            .putLong(KEY_LON, java.lang.Double.doubleToRawLongBits(place.longitude))
            .apply()
    }

    fun autoOnTimeZoneChange(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO, true)

    fun setAutoOnTimeZoneChange(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO, on).apply()
    }

    /**
     * Looks up the approximate location once (network provider: Wi-Fi or cell, no GPS) and names it.
     * [onResult] runs on a background thread with the place, or null if no fix was available.
     */
    fun lookUpOnce(context: Context, onResult: (SunPlace?) -> Unit) {
        val app = context.applicationContext
        Thread {
            try {
                ensurePermission(app)
                if (!hasPermission(app)) {
                    Log.w(TAG, "Location permission not granted")
                    onResult(null)
                    return@Thread
                }
                val lm = app.getSystemService(LocationManager::class.java)
                if (lm == null) {
                    onResult(null)
                    return@Thread
                }
                val providers = lm.getProviders(true)
                val provider = when {
                    LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in providers ->
                        LocationManager.FUSED_PROVIDER
                    else -> null
                }
                if (provider == null) {
                    Log.i(TAG, "No network location provider enabled")
                    onResult(null)
                    return@Thread
                }
                lm.getCurrentLocation(provider, null, Executors.newSingleThreadExecutor()) { loc ->
                    // No fresh fix (e.g. indoors with only GPS): fall back to the newest position Android already has.
                    val chosen = loc ?: providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                        .maxByOrNull { it.time }
                    onResult(chosen?.let { SunPlace(nameFor(app, it), it.latitude, it.longitude) })
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Location lookup failed", t)
                onResult(null)
            }
        }.start()
    }

    private fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Granted only when a lookup is first needed. Background access lets a time-zone change trigger a lookup. */
    private fun ensurePermission(context: Context) {
        if (hasPermission(context) &&
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val pkg = context.packageName
        for (perm in listOf("ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION")) {
            PServerClient.exec("pm grant $pkg android.permission.$perm", wait = true)
        }
    }

    @Suppress("DEPRECATION") // The listener form needs API 33; this already runs off the main thread.
    private fun nameFor(context: Context, loc: Location): String {
        val fallback = String.format(Locale.US, "%.2f, %.2f", loc.latitude, loc.longitude)
        if (!Geocoder.isPresent()) return fallback
        return runCatching {
            val address = Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
                ?.firstOrNull()
            val town = address?.locality ?: address?.subAdminArea ?: address?.adminArea
            listOfNotNull(town, address?.countryCode).joinToString(", ").ifEmpty { fallback }
        }.getOrDefault(fallback)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
