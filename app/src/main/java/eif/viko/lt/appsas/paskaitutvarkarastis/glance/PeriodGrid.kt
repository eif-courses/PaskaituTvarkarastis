package eif.viko.lt.appsas.paskaitutvarkarastis.glance

/**
 * VIKO's fixed period grid. The feed carries times on every lecture it has, so this is only
 * consulted for lectures the feed does NOT have: a change record that puts a lecture into a
 * slot where the timetable had none (see [AddedLectures]). Per-institution constant, like
 * the parity anchor; see glance-notes.md §5.
 */
object PeriodGrid {

    private val TIMES = mapOf(
        1 to ("08:30" to "10:00"),
        2 to ("10:15" to "11:45"),
        3 to ("12:30" to "14:00"),
        4 to ("14:15" to "15:45"),
        5 to ("16:00" to "17:30"),
        6 to ("17:45" to "19:15"),
        7 to ("19:30" to "21:00")
    )

    /** Start and end for a period number as the feed writes it ("2"), or null if unknown. */
    fun times(period: String): Pair<String, String>? = TIMES[period.trim().toIntOrNull()]
}
