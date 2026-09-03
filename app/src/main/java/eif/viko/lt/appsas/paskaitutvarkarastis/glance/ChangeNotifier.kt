package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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

    /**
     * [boundEntities] is how many distinct entities the widgets are bound to. With one, the
     * entity prefix on each line would only repeat the obvious and eat into the ~45
     * characters the shade shows.
     */
    suspend fun notifyNewMatches(context: Context, matches: List<MatchedChange>, boundEntities: Int) {
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

        post(context, fresh, showEntity = boundEntities > 1)
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

    private fun post(context: Context, fresh: List<MatchedChange>, showEntity: Boolean) {
        val manager = NotificationManagerCompat.from(context)

        // Covers both a denied POST_NOTIFICATIONS on Android 13+ and the user muting the
        // channel. Nothing is retried: the widget already shows the change either way.
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications disabled, skipping ${fresh.size} change(s)")
            return
        }

        ensureChannel(context)

        val lines = orderForDisplay(fresh).map { describe(context, it, showEntity) }
        val title = context.resources.getQuantityString(
            R.plurals.notif_title, fresh.size, fresh.size
        )

        // Purely informational: no tap target. The widget is the place to see a change in
        // context, and a tap that opened the app or guessed at a home page read as a bug.
        // Expanded, the notification already lists every change. Without a content intent
        // auto-cancel would never fire, so it is left off and dismissal is swipe only.
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.forEach { style.addLine(it) }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_refresh_24)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // The enabled-check above already covers a denied runtime permission in practice;
        // this is the explicit check the platform wants next to notify() on Android 13+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted, skipping ${fresh.size} change(s)")
            return
        }

        // One id, so a later sync replaces the summary instead of stacking a second one.
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onFailure { Log.w(TAG, "Could not post notification", it) }
    }

    /**
     * Earliest lecture first: by date, then by start time within the day. The first line
     * doubles as the collapsed notification's text, so it must be the most urgent change -
     * a cancellation tomorrow morning beats one next Friday. Dates are ISO from the feed
     * and times are zero-padded, so plain string order is chronological.
     */
    internal fun orderForDisplay(fresh: List<MatchedChange>): List<MatchedChange> =
        fresh.sortedWith(compareBy({ it.lecture.date }, { it.lecture.starttime }))

    /**
     * "Cancelled · Testinis dalykas · 09-03 10:15": the outcome first, because the shade cuts every
     * line at roughly 45 characters and the outcome is the one word that must survive. The
     * subject comes before the date - the reader knows when their lectures are, a date or a
     * period number alone does not say which lecture it is. The start time last: it costs
     * nothing when truncated and separates two changes to the same subject on one day.
     */
    private fun describe(context: Context, match: MatchedChange, showEntity: Boolean): String {
        val subject = match.lecture.subjectid.trim().ifBlank {
            context.getString(R.string.notif_lecture_fallback)
        }
        val what = when (ChangeMatcher.statusOf(match.change)) {
            ChangeStatus.CANCELLED -> context.getString(R.string.lesson_cancelled)
            ChangeStatus.CHANGED -> context.getString(
                R.string.widget_moved_to, match.change.auditorija.trim()
            )
        }
        val prefix = if (showEntity && match.entityLabel.isNotBlank()) {
            match.entityLabel.withoutAcademicTitle() + ": "
        } else {
            ""
        }
        return prefix + context.getString(
            R.string.notif_line, what, subject, shortDate(match.lecture.date),
            match.lecture.starttime.trim()
        )
    }

    /** "2026-09-03" -> "09-03"; anything that is not an ISO date is left alone. */
    internal fun shortDate(isoDate: String): String {
        val t = isoDate.trim()
        return if (Regex("""\d{4}-\d{2}-\d{2}""").matches(t)) t.substring(5) else t
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
