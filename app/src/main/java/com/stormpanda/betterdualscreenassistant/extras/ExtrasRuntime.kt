// SPDX-License-Identifier: GPL-3.0-or-later
// Fork-only addition (bee-san/better-dual-screen-assistant). Not part of upstream BDSA.
package com.stormpanda.betterdualscreenassistant.extras

import android.content.Context
import com.stormpanda.betterdualscreenassistant.extras.schedule.DisplaySchedules
import kotlinx.coroutines.CoroutineScope

/** Single entry point the foreground service calls to start and stop fork-only background features. */
object ExtrasRuntime {
    fun onServiceCreate(context: Context, scope: CoroutineScope) {
        DisplaySchedules.start(context, scope)
    }

    fun onServiceDestroy(context: Context) {
        DisplaySchedules.stop(context)
    }
}
