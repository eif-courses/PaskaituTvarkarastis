package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefreshChainTest {

    private val today = "2026-09-15"

    private fun lecture(date: String, start: String, end: String, subject: String = "Subject") =
        LecturesDto(date = date, starttime = start, endtime = end, subjectid = subject)

    /** Nothing in progress: the chain must end, not keep the worker alive for nothing. */
    @Test
    fun `no reschedule when nothing is in progress`() {
        assertNull(RefreshChain.nextDelaySeconds(emptyList(), today, "12:00"))
        // upcoming and finished today
        assertNull(RefreshChain.nextDelaySeconds(listOf(lecture(today, "12:30", "14:00")), today, "12:00"))
        assertNull(RefreshChain.nextDelaySeconds(listOf(lecture(today, "10:15", "11:45")), today, "12:00"))
        // in progress by the clock, but on another day
        assertNull(RefreshChain.nextDelaySeconds(listOf(lecture("2026-09-16", "11:00", "13:00")), today, "12:00"))
    }

    @Test
    fun `one step ahead while a lecture has more than a step left`() {
        val delay = RefreshChain.nextDelaySeconds(listOf(lecture(today, "10:15", "14:00")), today, "12:00")
        assertEquals(5 * 60L + 5, delay)
    }

    /** Ending sooner than a step: fire just past the end so the tint drops on time. */
    @Test
    fun `the lecture end wins when it is sooner than a step`() {
        val delay = RefreshChain.nextDelaySeconds(listOf(lecture(today, "10:15", "12:02")), today, "12:00")
        assertEquals(2 * 60L + 5, delay)
        // ends this very minute: still in progress by the clock, so a near-immediate render
        assertEquals(60L + 5, RefreshChain.nextDelaySeconds(listOf(lecture(today, "10:15", "12:00")), today, "11:59"))
    }

    @Test
    fun `the earliest end among several in-progress lectures decides`() {
        val lectures = listOf(
            lecture(today, "10:15", "14:00"),
            lecture(today, "11:30", "12:03"),
            lecture(today, "12:30", "15:45")   // upcoming, ignored
        )
        assertEquals(3 * 60L + 5, RefreshChain.nextDelaySeconds(lectures, today, "12:00"))
    }

    /** All-day placeholders never keep the chain alive. */
    @Test
    fun `placeholders are ignored`() {
        val rows = listOf(lecture(today, "00:00", "24:00", subject = "N/A"), lecture(today, "00:00", "24:00", subject = ""))
        assertNull(RefreshChain.nextDelaySeconds(rows, today, "12:00"))
    }

    /** Exactly at the end the lecture is finished (end <= now), so nothing is scheduled. */
    @Test
    fun `exactly at end is finished`() {
        assertNull(RefreshChain.nextDelaySeconds(listOf(lecture(today, "10:15", "12:00")), today, "12:00"))
    }
}
