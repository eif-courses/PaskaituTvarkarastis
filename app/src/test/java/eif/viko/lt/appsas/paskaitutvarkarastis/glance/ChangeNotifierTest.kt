package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangeNotifierTest {

    private fun match(date: String, start: String, subject: String) = MatchedChange(
        lecture = LecturesDto(date = date, starttime = start, subjectid = subject),
        change = ChangeDto()
    )

    /**
     * The first line is what the collapsed notification shows, so the order decides which
     * change the user sees without expanding: the earliest lecture, not the feed's order.
     */
    @Test
    fun `earliest lecture comes first, by date then start time`() {
        val friday = match("2026-09-11", "08:30", "next Friday")
        val tomorrowNoon = match("2026-09-04", "12:30", "tomorrow noon")
        val tomorrowMorning = match("2026-09-04", "08:30", "tomorrow morning")

        val ordered = ChangeNotifier.orderForDisplay(listOf(friday, tomorrowNoon, tomorrowMorning))

        assertEquals(
            listOf("tomorrow morning", "tomorrow noon", "next Friday"),
            ordered.map { it.lecture.subjectid }
        )
    }

    @Test
    fun `ordering is stable for equal date and time`() {
        val a = match("2026-09-04", "08:30", "a")
        val b = match("2026-09-04", "08:30", "b")
        assertEquals(listOf("a", "b"), ChangeNotifier.orderForDisplay(listOf(a, b)).map { it.lecture.subjectid })
        assertEquals(listOf("b", "a"), ChangeNotifier.orderForDisplay(listOf(b, a)).map { it.lecture.subjectid })
    }

    /** The line ends with MM-dd: the year is never news, and the shade's width is scarce. */
    @Test
    fun `date is shortened to month and day`() {
        assertEquals("09-03", ChangeNotifier.shortDate("2026-09-03"))
        assertEquals("09-03", ChangeNotifier.shortDate(" 2026-09-03 "))
        assertEquals("Mon Sep 7 2026", ChangeNotifier.shortDate("Mon Sep 7 2026"))
        assertEquals("", ChangeNotifier.shortDate(""))
    }
}
