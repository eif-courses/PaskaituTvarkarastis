package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Monday-to-Sunday week boundaries as yyyy-MM-dd strings, for filtering LecturesDto.date.
 *
 * Deliberately uses the DEFAULT timezone: "which week is the user looking at" is a local
 * question. This is not ChangeMatcher's UTC, which exists only to undo a JS serialization
 * artifact in the Firebase payload.
 *
 * java.util.Calendar rather than java.time — minSdk is 23 and desugaring is off.
 */
object WeekRange {

    private const val ISO_PATTERN = "yyyy-MM-dd"

    fun mondayOf(offsetWeeks: Int): String = mondayOf(offsetWeeks, Calendar.getInstance())

    fun sundayOf(offsetWeeks: Int): String = sundayOf(offsetWeeks, Calendar.getInstance())

    fun defaultOffset(): Int = defaultOffset(Calendar.getInstance())

    internal fun mondayOf(offsetWeeks: Int, anchor: Calendar): String =
        format(startOfWeek(anchor, offsetWeeks))

    internal fun sundayOf(offsetWeeks: Int, anchor: Calendar): String =
        format(startOfWeek(anchor, offsetWeeks).apply { add(Calendar.DAY_OF_YEAR, 6) })

    /** On the weekend the interesting timetable is the one starting Monday. */
    internal fun defaultOffset(anchor: Calendar): Int =
        when (anchor.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY, Calendar.SUNDAY -> 1
            else -> 0
        }

    private fun startOfWeek(anchor: Calendar, offsetWeeks: Int): Calendar {
        val cal = anchor.clone() as Calendar
        // Calendar.SUNDAY is 1 and MONDAY is 2, so +5 mod 7 maps Monday to 0 and Sunday to 6.
        // Calendar.set(DAY_OF_WEEK, MONDAY) would be wrong here: under a Sunday-first locale
        // it resolves to the *upcoming* Monday on Sundays.
        val daysSinceMonday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        // add() works in calendar days, so it does not drift across DST transitions the way
        // adding 7 * 24h of raw milliseconds would.
        cal.add(Calendar.DAY_OF_YEAR, -daysSinceMonday + offsetWeeks * 7)
        return cal
    }

    private fun format(cal: Calendar): String =
        SimpleDateFormat(ISO_PATTERN, Locale.US)
            .apply { timeZone = cal.timeZone }
            .format(cal.time)
}
