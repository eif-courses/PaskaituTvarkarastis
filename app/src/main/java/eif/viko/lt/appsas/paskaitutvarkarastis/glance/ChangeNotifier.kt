package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import eif.viko.lt.appsas.paskaitutvarkarastis.MainActivity
import eif.viko.lt.appsas.paskaitutvarkarastis.MainDataStorage
import eif.viko.lt.appsas.paskaitutvarkarastis.R

/** A change that actually lands on a lecture shown by one of the widgets. */
data class MatchedChange(
    val lecture: LecturesDto,
    val change: ChangeDto,
    /** Which group/teacher it was matched for, so a line can say which. */
    val entityLabel: String = ""
)

/**
 * Announces changes that affect the user's own timetable, once each.
 *
 * Matching is left entirely to [ChangeMatcher]: anything highlighted in the widget is worth a
 * notification, and anything that is not matched is not this user's problem.
 */
object ChangeNotifier {

    private const val TAG = "ChangeNotifier"
    private const val CHANNEL_ID = "timetable-changes"
    private const val NOTIFICATION_ID = 4711
    private const val SEEN_KEY = "SEEN_CHANGES"

    suspend fun notifyNewMatches(context: Context, matches: List<MatchedChange>) {
        val storage = MainDataStorage.getInstance(context)

        // Keyed by signature, so the same change matched by two widgets is one entry.
        val current = matches.associateBy { signatureOf(it.change) }
        val previouslySeen = storage.readString(SEEN_KEY)

        // Exactly what matches right now is stored, so signatures for changes the department
        // has withdrawn drop out on their own and the set cannot grow without bound.
        storage.writeString(SEEN_KEY, current.keys.sorted().joinToString("\n"))

        // A first run has nothing to compare against. Seeding silently avoids announcing the
        // whole existing backlog as though it had just been filed.
        if (previouslySeen == null) {
            Log.i(TAG, "Seeded ${current.size} known change(s) without notifying")
            return
        }

        val seen = previouslySeen.split("\n").filter { it.isNotBlank() }.toSet()
        val fresh = current.filterKeys { it !in seen }.values.toList()
        if (fresh.isEmpty()) return

        post(context, fresh)
    }

    /**
     * Identifies one change. The room and teacher are part of it on purpose: if the
     * department edits a change to a different room, that is news and should notify again.
     */
    private fun signatureOf(change: ChangeDto): String = listOf(
        ChangeMatcher.normalizeDate(change.date),
        change.paskaita.trim(),
        ChangeMatcher.normalizeGroup(change.grupe),
        change.auditorija.trim(),
        change.destytojas.trim()
    ).joinToString("|")

    private fun post(context: Context, fresh: List<MatchedChange>) {
        val manager = NotificationManagerCompat.from(context)

        // Covers both a denied POST_NOTIFICATIONS on Android 13+ and the user muting the
        // channel. Nothing is retried: the widget already shows the change either way.
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications disabled, skipping ${fresh.size} change(s)")
            return
        }

        ensureChannel(context)

        val lines = fresh.sortedBy { it.lecture.date }.map { describe(context, it) }
        val title = context.resources.getQuantityString(
            R.plurals.notif_title, fresh.size, fresh.size
        )

        val tap = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.forEach { style.addLine(it) }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_refresh_24)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(style)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // One id, so a later sync replaces the summary instead of stacking a second one.
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onFailure { Log.w(TAG, "Could not post notification", it) }
    }

    private fun describe(context: Context, match: MatchedChange): String {
        val subject = match.lecture.subjectid.ifBlank {
            context.getString(R.string.notif_lecture_fallback)
        }
        val period = match.lecture.uniperiod.trim()
        val what = when (ChangeMatcher.statusOf(match.change)) {
            ChangeStatus.CANCELLED -> context.getString(R.string.lesson_cancelled)
            ChangeStatus.CHANGED -> context.getString(
                R.string.widget_moved_to, match.change.auditorija.trim()
            )
        }
        val prefix = if (match.entityLabel.isBlank()) "" else match.entityLabel.withoutAcademicTitle() + ": "
        return prefix + context.getString(
            R.string.notif_line, match.lecture.date, period, subject, what
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
