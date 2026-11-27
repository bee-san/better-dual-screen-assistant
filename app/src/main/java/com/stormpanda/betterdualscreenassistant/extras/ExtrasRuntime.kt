// SPDX-License-Identifier: GPL-3.0-or-later
// Fork-only addition (bee-san/better-dual-screen-assistant). Not part of upstream BDSA.
package com.stormpanda.betterdualscreenassistant.extras

import android.content.Context
import android.util.Log
import com.kei.pulse.bdsa.PulseBridge
import com.stormpanda.betterdualscreenassistant.extras.schedule.DisplaySchedules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Single entry point the foreground service calls to start and stop fork-only background features. */
object ExtrasRuntime {
    private const val TAG = "BDSA-Extras"

    /** Gives PServerClient.ensurePermissionsGranted() time to grant Usage Access before PULSE checks it. */
    private const val START_DELAY_MS = 5_000L

    fun onServiceCreate(context: Context, scope: CoroutineScope) {
        DisplaySchedules.start(context, scope)
        scope.launch(Dispatchers.IO) {
            delay(START_DELAY_MS)
            runCatching { PulseBridge.ensureRunning(context) }
                .onFailure { Log.w(TAG, "Starting PULSE failed", it) }
        }
    }

    fun onServiceDestroy(context: Context) {
        DisplaySchedules.stop(context)
    }
}
