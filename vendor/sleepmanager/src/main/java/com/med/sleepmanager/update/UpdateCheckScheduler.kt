package com.med.sleepmanager.update

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

object UpdateCheckScheduler {
    private const val JOB_ID = 5220
    private const val PERIOD_MS = 24L * 60L * 60L * 1000L

    fun sync(context: Context) {
        val scheduler =
            context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

        // BDSA fork: bundled builds never check for updates, so don't keep a daily job that only returns Disabled.
        if (UpdateChecker.BUNDLED_IN_BDSA ||
            !com.med.sleepmanager.data.AppPreferences.automaticUpdateChecks(context)
        ) {
            scheduler.cancel(JOB_ID)
            return
        }

        if (scheduler.getPendingJob(JOB_ID) != null) return

        val job = JobInfo.Builder(
            JOB_ID,
            ComponentName(context, UpdateCheckJobService::class.java)
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(PERIOD_MS)
            .setPersisted(true)
            .build()

        scheduler.schedule(job)
    }
}
