// SPDX-License-Identifier: GPL-3.0-or-later
// Fork-only addition (bee-san/better-dual-screen-assistant). Not part of upstream BDSA.
package com.stormpanda.betterdualscreenassistant.extras

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.stormpanda.betterdualscreenassistant.R
import com.stormpanda.betterdualscreenassistant.extras.schedule.DisplaySchedules
import com.stormpanda.betterdualscreenassistant.extras.schedule.ScheduleConfig
import com.stormpanda.betterdualscreenassistant.extras.schedule.ScheduleMath
import com.stormpanda.betterdualscreenassistant.extras.schedule.ScheduleTarget
import com.stormpanda.betterdualscreenassistant.extras.schedule.SunLocation
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadStepperCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.firstDeckItem

private const val TIME_STEP_MINUTES = 15

/** Content of the fork-only "Extras" settings category. */
@Composable
internal fun ExtrasSettingsTabContent(injectOsTheme: Boolean) {
    val context = LocalContext.current
    DisplaySchedules.init(context)
    val configs by DisplaySchedules.configs.collectAsState()
    val sun by DisplaySchedules.sunToday.collectAsState()
    val sunPlace by DisplaySchedules.sunPlace.collectAsState()
    val autoLocate by DisplaySchedules.autoLocate.collectAsState()
    val lookup by DisplaySchedules.lookup.collectAsState()

    GamepadSectionHeader(text = stringResource(R.string.extras_apps_header))
    BuiltInApps.all.forEachIndexed { index, app ->
        GamepadActionCard(
            title = stringResource(app.titleRes),
            description = stringResource(app.descriptionRes),
            icon = app.icon,
            actionText = stringResource(R.string.extras_open),
            itemKey = "extras_app_${app.key}",
            onClick = { BuiltInApps.open(context, app) },
            modifier = if (index == 0) Modifier.firstDeckItem() else Modifier
        )
    }

    GamepadSectionHeader(text = stringResource(R.string.extras_schedules_header))

    ScheduleTarget.entries.forEachIndexed { index, target ->
        val cfg = configs[target] ?: ScheduleConfig()
        val title = stringResource(
            when (target) {
                ScheduleTarget.NIGHT_LIGHT -> R.string.extras_schedule_night_light
                ScheduleTarget.EXTRA_DIM -> R.string.extras_schedule_extra_dim
                ScheduleTarget.DARK_THEME -> R.string.extras_schedule_dark_theme
            }
        )
        val icon = when (target) {
            ScheduleTarget.NIGHT_LIGHT -> Icons.Rounded.NightsStay
            ScheduleTarget.EXTRA_DIM -> Icons.Rounded.BrightnessLow
            ScheduleTarget.DARK_THEME -> Icons.Rounded.Brightness4
        }
        val blockedByInjection = target == ScheduleTarget.DARK_THEME && injectOsTheme
        GamepadToggleCard(
            title = title,
            description = when {
                blockedByInjection -> stringResource(R.string.extras_schedule_dark_theme_blocked)
                cfg.followSun && sun != null -> stringResource(
                    R.string.extras_schedule_desc_sun,
                    ScheduleMath.format(sun!!.sunsetMinute),
                    ScheduleMath.format(sun!!.sunriseMinute)
                )
                else -> stringResource(
                    R.string.extras_schedule_desc,
                    ScheduleMath.format(cfg.startMinute),
                    ScheduleMath.format(cfg.endMinute)
                )
            },
            checked = cfg.enabled,
            icon = icon,
            enabled = !blockedByInjection,
            itemKey = "extras_schedule_${target.key}",
            onCheckedChange = { on -> DisplaySchedules.update(context, target) { it.copy(enabled = on) } },
        )
        if (cfg.enabled && !blockedByInjection) {
            GamepadToggleCard(
                title = stringResource(R.string.extras_schedule_sun_title, title),
                description = if (sun != null) {
                    stringResource(
                        R.string.extras_schedule_sun_desc,
                        ScheduleMath.format(sun!!.sunsetMinute),
                        ScheduleMath.format(sun!!.sunriseMinute),
                        sunPlace.name
                    )
                } else {
                    stringResource(
                        R.string.extras_schedule_sun_desc_waiting,
                        ScheduleMath.format(cfg.startMinute),
                        ScheduleMath.format(cfg.endMinute),
                        sunPlace.name
                    )
                },
                checked = cfg.followSun,
                icon = Icons.Rounded.WbTwilight,
                itemKey = "extras_schedule_${target.key}_sun",
                onCheckedChange = { on -> DisplaySchedules.update(context, target) { it.copy(followSun = on) } },
            )
        }
        if (cfg.enabled && !blockedByInjection && !cfg.followSun) {
            GamepadStepperCard(
                title = stringResource(R.string.extras_schedule_start, title),
                valueText = ScheduleMath.format(cfg.startMinute),
                icon = Icons.Rounded.Schedule,
                itemKey = "extras_schedule_${target.key}_start",
                onDecrement = {
                    DisplaySchedules.update(context, target) { it.copy(startMinute = it.startMinute - TIME_STEP_MINUTES) }
                },
                onIncrement = {
                    DisplaySchedules.update(context, target) { it.copy(startMinute = it.startMinute + TIME_STEP_MINUTES) }
                }
            )
            GamepadStepperCard(
                title = stringResource(R.string.extras_schedule_end, title),
                valueText = ScheduleMath.format(cfg.endMinute),
                icon = Icons.Rounded.Bedtime,
                itemKey = "extras_schedule_${target.key}_end",
                onDecrement = {
                    DisplaySchedules.update(context, target) { it.copy(endMinute = it.endMinute - TIME_STEP_MINUTES) }
                },
                onIncrement = {
                    DisplaySchedules.update(context, target) { it.copy(endMinute = it.endMinute + TIME_STEP_MINUTES) }
                }
            )
        }
    }

    if (configs.values.any { it.enabled && it.followSun }) {
        GamepadSectionHeader(text = stringResource(R.string.extras_sun_header))
        val presets = SunLocation.presets
        val index = presets.indexOfFirst { it.name == sunPlace.name }
        GamepadStepperCard(
            title = stringResource(R.string.extras_sun_place),
            valueText = sunPlace.name,
            description = stringResource(R.string.extras_sun_place_desc),
            icon = Icons.Rounded.Place,
            itemKey = "extras_sun_place",
            onDecrement = {
                val next = if (index <= 0) presets.lastIndex else index - 1
                DisplaySchedules.setSunPlace(context, presets[next])
            },
            onIncrement = {
                val next = if (index < 0 || index == presets.lastIndex) 0 else index + 1
                DisplaySchedules.setSunPlace(context, presets[next])
            }
        )
        GamepadActionCard(
            title = stringResource(R.string.extras_sun_locate),
            description = stringResource(
                if (lookup == DisplaySchedules.LookupState.FAILED) {
                    R.string.extras_sun_locate_failed
                } else {
                    R.string.extras_sun_locate_desc
                }
            ),
            icon = Icons.Rounded.MyLocation,
            actionText = stringResource(
                if (lookup == DisplaySchedules.LookupState.LOOKING) R.string.extras_sun_locating else R.string.extras_sun_locate_action
            ),
            itemKey = "extras_sun_locate",
            enabled = lookup != DisplaySchedules.LookupState.LOOKING,
            onClick = { DisplaySchedules.locateNow(context) }
        )
        GamepadToggleCard(
            title = stringResource(R.string.extras_sun_auto),
            description = stringResource(R.string.extras_sun_auto_desc),
            checked = autoLocate,
            icon = Icons.Rounded.TravelExplore,
            itemKey = "extras_sun_auto",
            onCheckedChange = { on -> DisplaySchedules.setAutoLocate(context, on) },
        )
    }
}
