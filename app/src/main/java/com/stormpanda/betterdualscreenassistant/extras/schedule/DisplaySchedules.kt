// SPDX-License-Identifier: GPL-3.0-or-later
// Fork-only addition (bee-san/better-dual-screen-assistant). Not part of upstream BDSA.
package com.stormpanda.betterdualscreenassistant.extras.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

/** Display features that BDSA can switch on and off at set times. */
enum class ScheduleTarget(val key: String) {
    NIGHT_LIGHT("night_light"),
    EXTRA_DIM("extra_dim"),
    DARK_THEME("dark_theme")
}

/**
 * A daily window. [startMinute] and [endMinute] are minutes after midnight; the window may wrap past midnight.
 * With [followSun] the window runs from today's sunset to sunrise at the chosen [SunLocation] place instead.
 */
data class ScheduleConfig(
    val enabled: Boolean = false,
    val startMinute: Int = DEFAULT_START,
    val endMinute: Int = DEFAULT_END,
    val followSun: Boolean = false
) {
    companion object {
        const val DEFAULT_START = 21 * 60
        const val DEFAULT_END = 7 * 60
    }
}

object ScheduleMath {
    const val MINUTES_PER_DAY = 24 * 60

    /** True when [nowMinute] falls inside [start, end). A window with start == end is empty. */
    fun isActive(nowMinute: Int, start: Int, end: Int): Boolean {
        if (start == end) return false
        return if (start < end) nowMinute in start until end else nowMinute >= start || nowMinute < end
    }

    fun wrap(minute: Int): Int = ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

    fun format(minute: Int): String {
        val m = wrap(minute)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /** Longest the schedule loop sleeps between checks, so a missed clock change (e.g. daylight saving) self-corrects. */
    const val MAX_WAIT_MS = 30 * 60_000L

    /**
     * How long to sleep until the next minute at which any of [windows] opens or closes (plus midnight when
     * [includeMidnight], so sun times are recomputed for the new day). Null when nothing can switch.
     * [msIntoMinute] is how far the clock already is into [nowMinute].
     */
    fun nextCheckDelayMs(
        nowMinute: Int,
        msIntoMinute: Long,
        windows: List<Pair<Int, Int>>,
        includeMidnight: Boolean
    ): Long? {
        val boundaries = windows.filter { (start, end) -> start != end }.flatMap { it.toList() } +
            if (includeMidnight) listOf(0) else emptyList()
        if (boundaries.isEmpty()) return null
        // A boundary at the current minute was just applied; the next time it comes round is tomorrow.
        val minutes = boundaries.minOf { wrap(it - nowMinute).takeIf { m -> m > 0 } ?: MINUTES_PER_DAY }
        return (minutes * 60_000L - msIntoMinute).coerceIn(1_000L, MAX_WAIT_MS)
    }
}

/**
 * Runs the Night Light / Extra Dim / Dark Theme schedules from inside the foreground service.
 *
 * Android's own Night Light and Dark Theme schedules do not fire on the AYN Thor, so this applies
 * the state itself. It is edge-triggered: it only writes when the scheduled state changes (the
 * window opens or closes, or the schedule is edited), so a manual toggle from the overlay sticks
 * until the next boundary.
 */
object DisplaySchedules {
    private const val TAG = "BDSA-Schedules"
    private const val PREFS = "bdsa_extras_schedules"

    private val _configs = MutableStateFlow<Map<ScheduleTarget, ScheduleConfig>>(emptyMap())
    val configs: StateFlow<Map<ScheduleTarget, ScheduleConfig>> = _configs.asStateFlow()

    /** The place sun times are worked out for (a chosen city, or a one-off location lookup). */
    private val _sunPlace = MutableStateFlow(SunLocation.DEFAULT)
    val sunPlace: StateFlow<SunPlace> = _sunPlace.asStateFlow()

    /** Today's sunrise and sunset at [sunPlace] in the device's time zone. Recomputed only when an input changes. */
    private val _sunToday = MutableStateFlow<SunTimes?>(null)
    val sunToday: StateFlow<SunTimes?> = _sunToday.asStateFlow()
    private var sunKey: Triple<LocalDate, ZoneId, SunPlace>? = null

    private val _autoLocate = MutableStateFlow(true)
    val autoLocate: StateFlow<Boolean> = _autoLocate.asStateFlow()

    enum class LookupState { IDLE, LOOKING, FAILED }

    private val _lookup = MutableStateFlow(LookupState.IDLE)
    val lookup: StateFlow<LookupState> = _lookup.asStateFlow()

    private var appContext: Context? = null
    private var loopJob: Job? = null
    private var screenReceiver: BroadcastReceiver? = null

    /** Wakes the schedule loop early so it re-checks and re-plans (screen on, clock change, settings edit). */
    private val replan = Channel<Unit>(Channel.CONFLATED)

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        _configs.value = ScheduleTarget.entries.associateWith { read(context, it) }
        _sunPlace.value = SunLocation.place(context)
        _autoLocate.value = SunLocation.autoOnTimeZoneChange(context)
        updateSunToday()
    }

    /** The window a schedule follows right now: sunset to sunrise when following the sun and sun times are known. */
    fun effectiveWindow(cfg: ScheduleConfig, sun: SunTimes? = _sunToday.value): Pair<Int, Int> =
        if (cfg.followSun && sun != null) sun.sunsetMinute to sun.sunriseMinute else cfg.startMinute to cfg.endMinute

    fun setSunPlace(context: Context, place: SunPlace) {
        init(context)
        SunLocation.setPlace(context, place)
        _sunPlace.value = place
        forgetAppliedState(context)
        evaluate(context)
        replan.trySend(Unit)
    }

    fun setAutoLocate(context: Context, on: Boolean) {
        init(context)
        SunLocation.setAutoOnTimeZoneChange(context, on)
        _autoLocate.value = on
    }

    /** One-off approximate location lookup; the result becomes the sun-times place. */
    fun locateNow(context: Context) {
        init(context)
        if (_lookup.value == LookupState.LOOKING) return
        _lookup.value = LookupState.LOOKING
        SunLocation.lookUpOnce(context) { place ->
            if (place != null) {
                setSunPlace(context, place)
                _lookup.value = LookupState.IDLE
            } else {
                _lookup.value = LookupState.FAILED
            }
        }
    }

    private fun updateSunToday() {
        val key = Triple(LocalDate.now(), ZoneId.systemDefault(), _sunPlace.value)
        if (key == sunKey) return
        sunKey = key
        _sunToday.value = SunTimes.compute(key.first, key.third.latitude, key.third.longitude, key.second)
    }

    /** Re-applies sun-following schedules at the next evaluation, e.g. after the place changes. */
    private fun forgetAppliedState(context: Context) {
        val edit = prefs(context).edit()
        for (target in ScheduleTarget.entries) {
            if (config(target).followSun) edit.remove("${target.key}_last")
        }
        edit.apply()
    }

    fun config(target: ScheduleTarget): ScheduleConfig = _configs.value[target] ?: ScheduleConfig()

    fun update(context: Context, target: ScheduleTarget, transform: (ScheduleConfig) -> ScheduleConfig) {
        init(context)
        val next = transform(config(target)).let {
            it.copy(startMinute = ScheduleMath.wrap(it.startMinute), endMinute = ScheduleMath.wrap(it.endMinute))
        }
        prefs(context).edit()
            .putBoolean("${target.key}_enabled", next.enabled)
            .putInt("${target.key}_start", next.startMinute)
            .putInt("${target.key}_end", next.endMinute)
            .putBoolean("${target.key}_sun", next.followSun)
            // Forget the last applied state so the edited schedule takes effect right away.
            .remove("${target.key}_last")
            .apply()
        _configs.value = _configs.value + (target to next)
        evaluate(context)
        replan.trySend(Unit)
    }

    /**
     * Starts the schedule loop. Call from the foreground service's onCreate. It sleeps until the next switch
     * time (at most [ScheduleMath.MAX_WAIT_MS]) rather than ticking every minute, and not at all while no
     * schedule is on. Its timer doesn't wake the device; after sleep, the screen-on broadcast catches up.
     */
    fun start(context: Context, scope: CoroutineScope) {
        init(context)
        loopJob?.cancel()
        loopJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                evaluate(context)
                val waitMs = nextCheckDelayMs()
                if (waitMs == null) replan.receive() else withTimeoutOrNull(waitMs) { replan.receive() }
            }
        }
        if (screenReceiver == null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    // Travelling: a new time zone usually means a new place, so look it up once.
                    if (intent.action == Intent.ACTION_TIMEZONE_CHANGED && _autoLocate.value &&
                        _configs.value.values.any { it.enabled && it.followSun }
                    ) {
                        locateNow(c)
                    }
                    replan.trySend(Unit)
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            runCatching {
                context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                screenReceiver = receiver
            }.onFailure { Log.w(TAG, "Could not register screen receiver", it) }
        }
    }

    fun stop(context: Context) {
        loopJob?.cancel()
        loopJob = null
        screenReceiver?.let { runCatching { context.applicationContext.unregisterReceiver(it) } }
        screenReceiver = null
    }

    private fun nextCheckDelayMs(): Long? {
        val cal = Calendar.getInstance()
        val enabled = _configs.value.values.filter { it.enabled }
        val sun = _sunToday.value
        return ScheduleMath.nextCheckDelayMs(
            nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE),
            msIntoMinute = cal.get(Calendar.SECOND) * 1000L + cal.get(Calendar.MILLISECOND),
            windows = enabled.map { effectiveWindow(it, sun) },
            includeMidnight = enabled.any { it.followSun }
        )
    }

    @Synchronized
    fun evaluate(context: Context) {
        init(context)
        val cal = Calendar.getInstance()
        val nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        updateSunToday()
        val sun = _sunToday.value
        val prefs = prefs(context)
        val repo = SystemSettingsRepository.getInstance(context.applicationContext)
        for (target in ScheduleTarget.entries) {
            val cfg = config(target)
            if (!cfg.enabled) continue
            val (start, end) = effectiveWindow(cfg, sun)
            val desired = ScheduleMath.isActive(nowMinute, start, end)
            val lastKey = "${target.key}_last"
            val last = if (prefs.contains(lastKey)) prefs.getBoolean(lastKey, false) else null
            if (last == desired) continue
            val applied = runCatching {
                when (target) {
                    ScheduleTarget.NIGHT_LIGHT -> { repo.setNightLight(desired); true }
                    ScheduleTarget.EXTRA_DIM -> { repo.setExtraDim(desired); true }
                    ScheduleTarget.DARK_THEME -> repo.setSystemDarkTheme(desired)
                }
            }.onFailure { Log.w(TAG, "Applying $target=$desired failed", it) }.getOrDefault(false)
            if (applied) {
                Log.i(TAG, "Schedule set $target=$desired")
                prefs.edit().putBoolean(lastKey, desired).apply()
            }
        }
    }

    private fun read(context: Context, target: ScheduleTarget): ScheduleConfig {
        val p = prefs(context)
        return ScheduleConfig(
            enabled = p.getBoolean("${target.key}_enabled", false),
            startMinute = p.getInt("${target.key}_start", ScheduleConfig.DEFAULT_START),
            endMinute = p.getInt("${target.key}_end", ScheduleConfig.DEFAULT_END),
            followSun = p.getBoolean("${target.key}_sun", false)
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
