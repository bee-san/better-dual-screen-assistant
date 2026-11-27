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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/** Display features that BDSA can switch on and off at set times. */
enum class ScheduleTarget(val key: String) {
    NIGHT_LIGHT("night_light"),
    EXTRA_DIM("extra_dim"),
    DARK_THEME("dark_theme")
}

/** A daily window. [startMinute] and [endMinute] are minutes after midnight; the window may wrap past midnight. */
data class ScheduleConfig(
    val enabled: Boolean = false,
    val startMinute: Int = DEFAULT_START,
    val endMinute: Int = DEFAULT_END
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

    private var appContext: Context? = null
    private var loopJob: Job? = null
    private var screenReceiver: BroadcastReceiver? = null

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        _configs.value = ScheduleTarget.entries.associateWith { read(context, it) }
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
            // Forget the last applied state so the edited schedule takes effect right away.
            .remove("${target.key}_last")
            .apply()
        _configs.value = _configs.value + (target to next)
        evaluate(context)
    }

    /** Starts the minute loop. Call from the foreground service's onCreate. */
    fun start(context: Context, scope: CoroutineScope) {
        init(context)
        loopJob?.cancel()
        loopJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                evaluate(context)
                val now = Calendar.getInstance()
                val msToNextMinute = 60_000L - (now.get(Calendar.SECOND) * 1000L + now.get(Calendar.MILLISECOND))
                delay(msToNextMinute.coerceIn(1_000L, 60_000L))
            }
        }
        if (screenReceiver == null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) = evaluate(c)
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

    @Synchronized
    fun evaluate(context: Context) {
        init(context)
        val cal = Calendar.getInstance()
        val nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val prefs = prefs(context)
        val repo = SystemSettingsRepository.getInstance(context.applicationContext)
        for (target in ScheduleTarget.entries) {
            val cfg = config(target)
            if (!cfg.enabled) continue
            val desired = ScheduleMath.isActive(nowMinute, cfg.startMinute, cfg.endMinute)
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
            endMinute = p.getInt("${target.key}_end", ScheduleConfig.DEFAULT_END)
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
