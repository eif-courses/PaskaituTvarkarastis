package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeriodLabelTest {

    /** The reported cases: Tuesday 10:15–14:00 is periods 2–3, Monday 12:30–15:45 is 3–4. */
    @Test
    fun `a two-period card renders as a range with an en dash`() {
        assertEquals("2–3", PeriodLabel.rangeOrNull("2", 2))
        assertEquals("3–4", PeriodLabel.rangeOrNull("3", 2))
        assertEquals("4–5", PeriodLabel.rangeOrNull(" 4 ", 2))
    }

    @Test
    fun `longer cards extend the range`() {
        assertEquals("1–4", PeriodLabel.rangeOrNull("1", 4))
        assertEquals("6–8", PeriodLabel.rangeOrNull("6", 3))
    }

    /** Older cached JSON has no durationperiods; the feed omits it for single periods. */
    @Test
    fun `absent zero or one duration is a single period`() {
        assertNull(PeriodLabel.rangeOrNull("2", null))
        assertNull(PeriodLabel.rangeOrNull("2", 0))
        assertNull(PeriodLabel.rangeOrNull("2", 1))
        assertNull(PeriodLabel.rangeOrNull("2", -1))
    }

    /** A non-numeric period cannot be extended, so it falls back rather than printing "x–?". */
    @Test
    fun `a non-numeric period is left to the single rendering`() {
        assertNull(PeriodLabel.rangeOrNull("", 2))
        assertNull(PeriodLabel.rangeOrNull("ad", 2))
        assertNull(PeriodLabel.rangeOrNull("2a", 2))
    }
}
