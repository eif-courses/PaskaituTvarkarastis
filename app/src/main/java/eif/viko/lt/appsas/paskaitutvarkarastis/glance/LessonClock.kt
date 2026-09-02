package eif.viko.lt.appsas.paskaitutvarkarastis.glance

/**
 * Time-of-day comparisons on the feed's "HH:mm" strings. Pure, so it can be unit tested
 * away from the composable that uses it.
 */
object LessonClock {

    /**
     * Whether a lecture ending at [endTime] is over at [now]. Both are zero-padded "HH:mm",
     * which makes plain string comparison correct.
     *
     * "24:00" is deliberately not normalised. The feed only emits it on the all-day
     * placeholder rows, which are filtered out before this is ever consulted; and if a real
     * lecture ever did end at 24:00, "not finished at any point today" is the right answer,
     * because it is still running at 23:59. Clamping it to 23:59 would mute it a minute early.
     */
    fun hasEnded(endTime: String, now: String): Boolean = endTime.trim() <= now.trim()

    /** startTime <= now < endTime. Sits between finished and upcoming. */
    fun isInProgress(startTime: String, endTime: String, now: String): Boolean =
        startTime.trim() <= now.trim() && !hasEnded(endTime, now)
}
