// SPDX-License-Identifier: GPL-3.0-or-later
// Fork-only addition (bee-san/better-dual-screen-assistant). Not part of upstream BDSA.
package com.stormpanda.betterdualscreenassistant.extras

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.Display
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.ui.graphics.vector.ImageVector
import com.kei.pulse.bdsa.PulseBridge
import com.stormpanda.betterdualscreenassistant.R

/** Apps vendored into this fork as library modules, each opened on the top screen in its own task. */
object BuiltInApps {
    data class App(
        val key: String,
        @param:StringRes val titleRes: Int,
        @param:StringRes val descriptionRes: Int,
        val icon: ImageVector,
        val intent: (Context) -> Intent
    )

    val PULSE = App("pulse", R.string.extras_app_pulse, R.string.extras_app_pulse_desc, Icons.Rounded.Speed) {
        PulseBridge.launchIntent(it)
    }

    val SLEEP_MANAGER = App(
        "sleepmanager",
        R.string.extras_app_sleepmanager,
        R.string.extras_app_sleepmanager_desc,
        Icons.Rounded.Bedtime
    ) {
        Intent(it, com.med.sleepmanager.MainActivity::class.java)
    }

    val all: List<App> = listOf(PULSE, SLEEP_MANAGER)

    fun open(context: Context, app: App) {
        try {
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            context.startActivity(app.intent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle())
        } catch (t: Throwable) {
            Log.w("BDSA-Extras", "Opening ${app.key} failed", t)
        }
    }
}
