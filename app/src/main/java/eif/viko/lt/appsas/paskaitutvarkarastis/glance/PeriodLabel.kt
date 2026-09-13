package eif.viko.lt.appsas.paskaitutvarkarastis.glance

/**
 * The period text for a lesson row: "2" for a single period, "2–3" for a card that spans
 * consecutive periods.
 *
 * The feed (aSc Timetables export) delivers a double lecture as one record with the block's
 * outer times, `uniperiod` = first period and `durationperiods` = how many. The row keeps
 * the block times - that is the department's card - but the label must not claim a single
 * period for a block of two. Kept free of Android types so it can be unit tested.
 */
object PeriodLabel {

    /**
     * The range end for a multi-period card, or null when the row is a plain single period:
     * [durationperiods] absent (older cached JSON), zero, one, or a [uniperiod] that is not
     * a number. Callers render null with the single-period string exactly as before.
     */
    fun rangeOrNull(uniperiod: String, durationperiods: Int?): String? {
        val first = uniperiod.trim().toIntOrNull() ?: return null
        val span = durationperiods ?: return null
        if (span < 2) return null
        return "$first–${first + span - 1}"
    }
}
