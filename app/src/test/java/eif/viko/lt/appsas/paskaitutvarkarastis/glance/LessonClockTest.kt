package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonClockTest {

    /**
     * The one case string comparison could get wrong. "24:00" sorts above every real time of
     * day, so it never reads as finished - and that is the correct outcome, not a bug to
     * paper over: a lecture ending at 24:00 is still in progress at 23:59.
     */
    @Test
    fun `an end time of 24 00 is never finished during the day`() {
        assertFalse(LessonClock.hasEnded("24:00", "23:59"))
        assertFalse(LessonClock.hasEnded("24:00", "08:00"))

        // The ordinary cases the rule exists for.
        assertTrue(LessonClock.hasEnded("10:00", "10:00"))   // ends exactly now: over
        assertTrue(LessonClock.hasEnded("10:00", "10:01"))
        assertFalse(LessonClock.hasEnded("10:00", "09:59"))  // in progress
        assertFalse(LessonClock.hasEnded("21:34", "20:50"))  // the row from the screenshot
    }

    @Test
    fun `in progress is start inclusive and end exclusive`() {
        assertTrue(LessonClock.isInProgress("10:15", "11:45", "10:15"))   // just started
        assertTrue(LessonClock.isInProgress("10:15", "11:45", "11:44"))
        assertFalse(LessonClock.isInProgress("10:15", "11:45", "11:45"))  // ended: finished, not in progress
        assertFalse(LessonClock.isInProgress("10:15", "11:45", "10:14"))  // upcoming
        assertTrue(LessonClock.isInProgress("22:00", "24:00", "23:59"))   // consistent with hasEnded
    }
}
