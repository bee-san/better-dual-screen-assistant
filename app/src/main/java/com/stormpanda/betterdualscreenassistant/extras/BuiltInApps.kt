// SPDX-License-Identifier: GPL-3.0-or-later
// Fork-only addition (bee-san/better-dual-screen-assistant). Not part of upstream BDSA.
package com.stormpanda.betterdualscreenassistant.extras

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.ui.graphics.vector.ImageVector
import com.androosio.thortune.bdsa.ThorTuneBridge
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

    val THOR_TUNE = App(
        "thortune",
        R.string.extras_app_thortune,
        R.string.extras_app_thortune_desc,
        Icons.Rounded.GraphicEq
    ) {
        ThorTuneBridge.launchIntent(it)
    }

    val all: List<App> = listOf(PULSE, THOR_TUNE, SLEEP_MANAGER)

    fun open(context: Context, app: App) {
        if (app == THOR_TUNE) {
            // ThorTune's scripts and engine must be on disk before its UI runs them.
            Thread {
                runCatching { ThorTuneBridge.stageAssets(context) }
                    .onFailure { Log.w("BDSA-Extras", "Staging ThorTune assets failed", it) }
                Handler(Looper.getMainLooper()).post { start(context, app) }
            }.start()
        } else {
            start(context, app)
        }
    }

    /**
     * From BDSA's settings (an Activity) the app opens in that task, so BACK returns to Extras.
     * From the bottom-screen panel (a service context) it gets its own task, so BACK returns to the game.
     */
    private fun start(context: Context, app: App) {
        try {
            val intent = app.intent(context)
            val options = ActivityOptions.makeBasic()
            if (context.findActivity() != null) {
                intent.flags = intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv()
                keepSettingsOnNextStop = true
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            }
            context.startActivity(intent, options.toBundle())
        } catch (t: Throwable) {
            keepSettingsOnNextStop = false
            Log.w("BDSA-Extras", "Opening ${app.key} failed", t)
        }
    }

    /** Set while a built-in app opens on top of settings, which otherwise closes its task when stopped. */
    @Volatile
    private var keepSettingsOnNextStop = false

    /** Called by MainActivity.onStop: true once if a built-in app just opened on top of it. */
    fun consumeKeepSettingsOpen(): Boolean {
        val keep = keepSettingsOnNextStop
        keepSettingsOnNextStop = false
        return keep
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
