package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.Gson
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Best-effort re-render while a lecture is running, so the in-progress tint and the muting
 * do not have to wait for the next 15-minute sync.
 *
 * Schedules a network-free re-render - no fetch, just RemoteViews from the cached state -
 * [STEP_MINUTES] ahead, or sooner if a lecture ends first, and only while something is in
 * progress. When nothing is, it does not reschedule and the chain dies.
 *
 * Best effort, not a guarantee: on Android 14+ JobScheduler holds delayed regular jobs for
 * batching and runs them when it next runs a batch, in practice with the periodic sync.
 * See glance-notes.md §6. Nothing visible depends on its timing; when it does run near a
 * lecture's end, the tint drops then instead of up to 15 minutes later.
 */
object RefreshChain {

    /** How far ahead a re-render is asked for while a lecture is running. */
    const val STEP_MINUTES = 5

    private const val TAG = "RefreshChain"
    private const val UNIQUE_NAME = "widget-refresh"

    /** A few seconds past the boundary, so the render sees the lecture as ended, not ending. */
    private const val SLACK_SECONDS = 5L

    /**
     * Seconds until the next re-render, or null when no lecture is in progress right now:
     * the sooner of one step and the earliest end among in-progress lectures. Pure, so the
     * decision is unit tested; [schedule] feeds it the cached lectures and the clock.
     */
    fun nextDelaySeconds(lectures: List<LecturesDto>, todayIso: String, nowHHmm: String): Long? {
        val endsIn = lectures.asSequence()
            .filter { it.date == todayIso && !it.isPlaceholder() }
            .filter { LessonClock.isInProgress(it.starttime, it.endtime, nowHHmm) }
            .map { LessonClock.minutesBetween(nowHHmm, it.endtime) }
            .minOrNull() ?: return null
        val stepSeconds = STEP_MINUTES * 60L
        return minOf(stepSeconds, endsIn * 60L) + SLACK_SECONDS
    }

    /** Reads every widget's cached lectures and enqueues the next re-render if one is due. */
    suspend fun schedule(context: Context) {
        val delay = runCatching { nextDelaySecondsFromState(context) }
            .onFailure { Log.w(TAG, "Could not read widget state", it) }
            .getOrNull()
        if (delay == null) {
            Log.d(TAG, "Nothing in progress; chain ends")
            return
        }
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(delay, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        Log.i(TAG, "Re-render in ${delay}s")
    }

    private suspend fun nextDelaySecondsFromState(context: Context): Long? {
        val manager = GlanceAppWidgetManager(context)
        val gson = Gson()
        val lectures = manager.getGlanceIds(TimetableWidget::class.java).flatMap { id ->
            val prefs: Preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
            val json = prefs[TimetableWidget.countKey] ?: return@flatMap emptyList()
            runCatching { gson.fromJson(json, Array<LecturesDto>::class.java).toList() }
                .getOrDefault(emptyList())
        }
        val now = LocalTime.now()
        return nextDelaySeconds(
            lectures,
            LocalDate.now().toString(),
            String.format(Locale.ROOT, "%02d:%02d", now.hour, now.minute)
        )
    }

    private fun LecturesDto.isPlaceholder(): Boolean =
        subjectid.isBlank() || subjectid.equals("N/A", ignoreCase = true)
}

/** Re-renders every widget from cached state, then asks [RefreshChain] whether to go again. */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        TimetableWidget.updateAll(applicationContext)
        RefreshChain.schedule(applicationContext)
        return Result.success()
    }
}
