package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WeekParityTest {

    /** Pins the anchor itself: a change here is a deliberate per-term edit. */
    @Test
    fun `anchor is a Monday and renders as II`() {
        // 1970-01-01 was a Thursday, so a Monday's epoch day satisfies (day + 3) % 7 == 0.
        assertEquals(0L, (WeekParity.epochDay(WeekParity.ANCHOR_MONDAY) + 3) % 7)
        assertEquals("II", WeekParity.label(WeekParity.ANCHOR_MONDAY))
        assertEquals(1, WeekParity.ANCHOR_PARITY)
    }

    @Test
    fun `epoch day matches known dates`() {
        assertEquals(0L, WeekParity.epochDay("1970-01-01"))
        assertEquals(20454L, WeekParity.epochDay("2026-01-01"))   // 2024-01-01 is 19723; +366 +365
        assertEquals(20699L, WeekParity.epochDay("2026-09-03"))   // day-of-year 246 -> 20454 + 245
        assertEquals(59L, WeekParity.epochDay("1970-03-01"))
        assertEquals(-1L, WeekParity.epochDay("1969-12-31"))
    }

    @Test
    fun `every day of a week shares the anchor week's parity`() {
        assertEquals("II", WeekParity.label("2026-08-31"))   // Monday
        for (day in 1..6) assertEquals("II", WeekParity.label("2026-09-0$day"))   // Tue..Sun
        assertEquals("I", WeekParity.label("2026-09-07"))   // next Monday flips
        assertEquals("I", WeekParity.label("2026-09-13"))   // ... through its Sunday
        assertEquals("II", WeekParity.label("2026-09-14"))
    }

    @Test
    fun `weeks before the anchor alternate backwards`() {
        assertEquals(-1, WeekParity.weeksBetween("2026-08-31", "2026-08-30"))
        assertEquals(-1, WeekParity.weeksBetween("2026-08-31", "2026-08-24"))
        assertEquals(-2, WeekParity.weeksBetween("2026-08-31", "2026-08-23"))
        assertEquals("I", WeekParity.label("2026-08-24"))
        assertEquals("II", WeekParity.label("2026-08-17"))
    }

    /**
     * Term boundary and new year. 2026 has an ISO week 53: the week of 2026-12-28 is ISO
     * week 53 and the week of 2027-01-04 is ISO week 1, so ISO arithmetic would give both
     * the same parity. Plain 7-day counting keeps them alternating.
     */
    @Test
    fun `consecutive weeks alternate across the ISO year boundary`() {
        assertEquals(17, WeekParity.weeksBetween("2026-08-31", "2026-12-28"))
        assertEquals(18, WeekParity.weeksBetween("2026-08-31", "2027-01-04"))
        assertNotEquals(WeekParity.of("2026-12-28"), WeekParity.of("2027-01-04"))
        assertEquals("I", WeekParity.label("2026-12-28"))    // (1 + 17) % 2 = 0
        assertEquals("II", WeekParity.label("2027-01-04"))   // (1 + 18) % 2 = 1
    }

    @Test
    fun `spring term weeks are still counted from the same anchor`() {
        // 2027-02-01 is 22 weeks after the anchor: (1 + 22) % 2 = 1 -> "II".
        assertEquals(22, WeekParity.weeksBetween("2026-08-31", "2027-02-01"))
        assertEquals("II", WeekParity.label("2027-02-01"))
        // A leap day in between (2028-02-29) does not disturb the 7-day cadence.
        assertEquals(78, WeekParity.weeksBetween("2026-08-31", "2028-02-28"))
        assertEquals(79, WeekParity.weeksBetween("2026-08-31", "2028-03-06"))
    }

    @Test
    fun `daylight saving changes do not shift a week`() {
        // Europe/Vilnius leaves DST on 2026-10-25 (Sunday) and enters on 2027-03-28.
        assertEquals(7, WeekParity.weeksBetween("2026-08-31", "2026-10-19"))
        assertEquals(8, WeekParity.weeksBetween("2026-08-31", "2026-10-26"))
        assertEquals(WeekParity.of("2026-10-26"), WeekParity.of("2026-11-01"))
    }
}
