package eif.viko.lt.appsas.paskaitutvarkarastis.glance

/**
 * The muted line under a day header: how many lectures, and when the day starts and ends.
 *
 * The span (first start to last end) rather than a sum of lengths: a day of three
 * 90-minute lectures with a two-hour hole is a long day, and "4h 30m" would call it a
 * short one. The line answers "when does my day start and end". Cancelled lectures are
 * left out of both the count and the span: they are not happening, and their rows are
 * already struck through. Pure, so it is unit tested.
 */
object DaySummary {

    /** [firstStart] / [lastEnd] are "HH:mm" from the feed, or null when nothing counts. */
    data class Totals(val lectures: Int, val firstStart: String?, val lastEnd: String?)

    fun of(lectures: List<LecturesDto>, isCancelled: (LecturesDto) -> Boolean): Totals {
        var count = 0
        var first: String? = null
        var last: String? = null
        for (lecture in lectures) {
            if (isCancelled(lecture)) continue
            count += 1
            val start = lecture.starttime.trim()
            val end = lecture.endtime.trim()
            // Zero-padded "HH:mm" compares correctly as plain strings; blanks are skipped.
            if (start.isNotEmpty() && (first == null || start < first)) first = start
            if (end.isNotEmpty() && (last == null || end > last)) last = end
        }
        return Totals(count, first, last)
    }
}
