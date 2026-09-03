package eif.viko.lt.appsas.paskaitutvarkarastis.glance

/**
 * The I / II week parity, derived on the device from a per-term anchor.
 *
 * The backend's `currentweek` value is (ISO week + a Firebase node) % 2, and that node was
 * changed on a Thursday once, flipping the header mid-week. Parity is the one thing on the
 * header the user actually reads, so it is computed here from a Monday whose parity is
 * known and never read from the network again for display.
 *
 * ## PER-TERM CONSTANT - update at the start of every term
 *
 * [ANCHOR_MONDAY] is a Monday and [ANCHOR_PARITY] is that week's parity as VIKO counts it
 * (0 = "I", 1 = "II"). Weeks alternate from there in plain 7-day steps. If the header ever
 * drifts from the official timetable, this pair is what changed: a new term, or a break
 * that the department did not count as a week.
 *
 * Current anchor: autumn term 2026/27. The week of 2026-08-31 was rendered as "II"
 * throughout that week from the backend's original data; if VIKO counts it as "I", flip
 * [ANCHOR_PARITY] to 0 and nothing else.
 *
 * Arithmetic is on epoch days, not ISO week numbers, so a year boundary (2026 has an ISO
 * week 53) changes nothing. Kept free of Android and java.time so it runs on API 23 and
 * in plain unit tests.
 */
object WeekParity {

    /** Monday of the anchor week, ISO yyyy-MM-dd. Must be a Monday. */
    const val ANCHOR_MONDAY = "2026-08-31"

    /** Parity of the anchor week: 0 renders as "I", 1 as "II". */
    const val ANCHOR_PARITY = 1

    /** Parity (0 or 1) of the week containing [isoDate]; any day of the week gives the same. */
    fun of(isoDate: String): Int =
        Math.floorMod(ANCHOR_PARITY + weeksBetween(ANCHOR_MONDAY, isoDate), 2)

    /** "I" or "II" for the week containing [isoDate]. */
    fun label(isoDate: String): String = if (of(isoDate) == 0) "I" else "II"

    /**
     * Whole weeks from the week of [fromIso] to the week of [toIso], negative when [toIso]
     * is earlier. [fromIso] is expected to be a Monday so that week boundaries fall on
     * Mondays; the division floors, so every day Monday..Sunday lands in the same week.
     */
    internal fun weeksBetween(fromIso: String, toIso: String): Int =
        Math.floorDiv(epochDay(toIso) - epochDay(fromIso), 7L).toInt()

    /** Days since 1970-01-01 for a proleptic Gregorian yyyy-MM-dd; no calendar library. */
    internal fun epochDay(isoDate: String): Long {
        val parts = isoDate.trim().split("-")
        require(parts.size == 3) { "expected yyyy-MM-dd, got '$isoDate'" }
        val y0 = parts[0].toInt()
        val m = parts[1].toInt()
        val d = parts[2].toInt()
        // Howard Hinnant's days_from_civil: years start in March so the leap day is last.
        val y = if (m <= 2) y0 - 1 else y0
        val era = Math.floorDiv(y, 400)
        val yoe = y - era * 400
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }
}
