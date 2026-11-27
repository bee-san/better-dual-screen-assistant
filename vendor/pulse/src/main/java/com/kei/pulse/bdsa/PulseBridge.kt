// SPDX-License-Identifier: GPL-2.0-or-later
// Added for the bee-san fork of Better Dual Screen Assistant: lets the host app start PULSE's
// background services and open its UI without a separate launcher entry.
package com.kei.pulse.bdsa

import android.content.Context
import android.content.Intent
import com.kei.pulse.AppContainer
import com.kei.pulse.MainActivity
import com.kei.pulse.appwatch.ForegroundAppMonitorService
import com.kei.pulse.appwatch.WatcherActivation
import com.kei.pulse.sleep.SleepProfileMonitorService
import kotlinx.coroutines.flow.first

object PulseBridge {
    /** Starts PULSE's watcher (AutoTDP, per-app, fan/RGB) and sleep monitor when their settings call for it. */
    suspend fun ensureRunning(context: Context) {
        val app = context.applicationContext
        val container = AppContainer(app)
        val settings = container.settingsStorage.settings.first()
        if (settings.sleepProfileEnabled) {
            SleepProfileMonitorService.start(app)
        }
        val shouldStart = WatcherActivation.shouldRun(
            perAppEnabled = container.perAppConfigStorage.enabled.first(),
            hasPerAppConfigs = container.perAppConfigStorage.configs.first().isNotEmpty(),
            settings = settings,
            hasUsageAccess = ForegroundAppMonitorService.hasUsageAccess(app),
        )
        if (shouldStart) {
            ForegroundAppMonitorService.start(app)
        }
    }

    fun launchIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
