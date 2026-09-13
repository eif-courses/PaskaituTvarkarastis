package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import org.junit.Assert.assertEquals
import org.junit.Test

class DaySummaryTest {

    private fun lecture(start: String, end: String, subject: String = "s") =
        LecturesDto(date = "2026-09-16", starttime = start, endtime = end, subjectid = subject)

    /** Wednesday 09-16: three lectures with a two-hour hole. The span is the day, not the sum. */
    @Test
    fun `span is first start to last end`() {
        val day = listOf(lecture("10:15", "11:45"), lecture("12:30", "14:00"), lecture("16:00", "17:30"))
        assertEquals(DaySummary.Totals(3, "10:15", "17:30"), DaySummary.of(day) { false })
    }

    /** Order in the feed does not matter. */
    @Test
    fun `span is independent of feed order`() {
        val day = listOf(lecture("16:00", "17:30"), lecture("10:15", "11:45"), lecture("12:30", "14:00"))
        assertEquals(DaySummary.Totals(3, "10:15", "17:30"), DaySummary.of(day) { false })
    }

    /** Cancelled lectures are out of the count and cannot stretch the span. */
    @Test
    fun `cancelled lectures are excluded from count and span`() {
        val day = listOf(lecture("08:30", "10:00", "gone"), lecture("10:15", "11:45"), lecture("12:30", "14:00"))
        assertEquals(DaySummary.Totals(2, "10:15", "14:00"), DaySummary.of(day) { it.subjectid == "gone" })
        assertEquals(DaySummary.Totals(0, null, null), DaySummary.of(day) { true })
    }

    @Test
    fun `a single lecture spans itself`() {
        assertEquals(DaySummary.Totals(1, "08:30", "10:00"), DaySummary.of(listOf(lecture("08:30", "10:00"))) { false })
    }

    @Test
    fun `blank times count the lecture but do not enter the span`() {
        val day = listOf(lecture("10:15", "11:45"), lecture("", ""))
        assertEquals(DaySummary.Totals(2, "10:15", "11:45"), DaySummary.of(day) { false })
    }
}
