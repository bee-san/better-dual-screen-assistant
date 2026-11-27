// SPDX-License-Identifier: GPL-2.0-or-later
// Added for the bee-san fork of Better Dual Screen Assistant: replaces ThorTune's Application class
// (asset staging) and launcher activity so ThorTune can live inside BDSA.
package com.androosio.thortune.bdsa

import android.content.Context
import android.content.Intent
import com.androosio.thortune.MainActivity
import com.androosio.thortune.utils.copyAssetFolderToFilesDir
import java.io.File

object ThorTuneBridge {
    private const val MARKER = "app/.staged_version"

    /**
     * Copies ThorTune's bundled scripts, configs, engine and Manager APK into filesDir. Upstream did
     * this on every process start; here it runs once per installed build because the assets are
     * ~38 MB and BDSA's process starts far more often. Call off the main thread.
     */
    @Synchronized
    fun stageAssets(context: Context) {
        val app = context.applicationContext
        val stamp = app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime.toString()
        val marker = File(app.filesDir, MARKER)
        if (marker.isFile && marker.readText() == stamp) return
        copyAssetFolderToFilesDir(app, "app")
        marker.parentFile?.mkdirs()
        marker.writeText(stamp)
    }

    fun launchIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
