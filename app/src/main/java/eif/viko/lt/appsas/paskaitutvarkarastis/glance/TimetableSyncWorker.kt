package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Keeps the widget current in the background, so it does not depend on the refresh tap. */
class TimetableSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (TimetableSync.syncTimetable(applicationContext)) {
        // Nothing was stored, so there is nothing to show: worth another attempt.
        SyncOutcome.TIMETABLE_UNAVAILABLE -> Result.retry()

        // Lectures are stored and rendering. Retrying only to re-fetch the changes we
        // already failed to get would spend battery for no visible gain, and the next
        // periodic run picks them up anyway.
        SyncOutcome.SUCCESS,
        SyncOutcome.CHANGES_UNAVAILABLE,
        SyncOutcome.NO_TEACHER_SELECTED -> {
            TimetableWidget.updateAll(applicationContext)
            Result.success()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "timetable-sync"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<TimetableSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        // The 15-minute floor stays: Doze already stretches it to a few
                        // runs a day when idle, and the change notifications depend on it
                        // while the phone is in use. This is the one courtesy that costs
                        // nothing.
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                // Backs off 15m, 30m, 1h, 2h, 4h, then WorkManager's 5h ceiling, so a
                // sustained outage does not keep hitting the API every 15 minutes.
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
