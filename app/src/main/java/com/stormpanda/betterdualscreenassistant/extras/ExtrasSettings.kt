// SPDX-License-Identifier: GPL-3.0-or-later
// Fork-only addition (bee-san/better-dual-screen-assistant). Not part of upstream BDSA.
package com.stormpanda.betterdualscreenassistant.extras

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Schedule
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
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadStepperCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.firstDeckItem

private const val TIME_STEP_MINUTES = 15

/** Content of the fork-only "Extras" settings category. */
@Composable
internal fun ExtrasSettingsTabContent(
    injectOsTheme: Boolean,
    extraCards: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    DisplaySchedules.init(context)
    val configs by DisplaySchedules.configs.collectAsState()

    extraCards()

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
            description = if (blockedByInjection) {
                stringResource(R.string.extras_schedule_dark_theme_blocked)
            } else {
                stringResource(
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
            modifier = if (index == 0) Modifier.firstDeckItem() else Modifier
        )
        if (cfg.enabled && !blockedByInjection) {
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
}
