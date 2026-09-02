package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * How the "updated …" caption describes the last successful sync. Pure, so the threshold
 * and the two forms are unit tested rather than waited twelve hours for.
 */
object SyncAge {

    /** Beyond this the exact time stops meaning much, and the date is the honest signal. */
    const val STALE_AFTER_MS = 12L * 60 * 60 * 1000

    /**
     * Old AND on a different calendar day. A sync from this morning, however many hours ago,
     * keeps its time: "updated 09-02" shown on 09-02 blurs a precise fact into a vaguer one.
     */
    fun isStale(syncedAtMillis: Long, nowMillis: Long): Boolean =
        nowMillis - syncedAtMillis > STALE_AFTER_MS && !sameCalendarDay(syncedAtMillis, nowMillis)

    private fun sameCalendarDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * "HH:mm" while fresh, "MM-dd" once stale - the same date shape the rest of the widget
     * uses, so a stale caption reads as a date at a glance rather than as a time that
     * happens to be far away.
     */
    fun label(syncedAtMillis: Long, nowMillis: Long): String {
        val pattern = if (isStale(syncedAtMillis, nowMillis)) "MM-dd" else "HH:mm"
        return SimpleDateFormat(pattern, Locale.US).format(Date(syncedAtMillis))
    }
}
