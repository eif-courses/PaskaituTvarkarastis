package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class WeekRangeTest {

    private val vilnius: TimeZone = TimeZone.getTimeZone("Europe/Vilnius")

    /** A fixed instant, so nothing here depends on the day the suite runs. */
    private fun anchor(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
        minute: Int = 0,
        zone: TimeZone = vilnius
    ): Calendar = Calendar.getInstance(zone, Locale.US).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }

    // --- weekday anchors, offsets 0 and 1 -------------------------------------------------

    @Test
    fun `Monday anchor resolves to its own week`() {
        val monday = anchor(2026, 8, 17)
        assertEquals("2026-08-17", WeekRange.mondayOf(0, monday))
        assertEquals("2026-08-23", WeekRange.sundayOf(0, monday))
        assertEquals("2026-08-24", WeekRange.mondayOf(1, monday))
        assertEquals("2026-08-30", WeekRange.sundayOf(1, monday))
    }

    @Test
    fun `Wednesday anchor resolves back to Monday`() {
        val wednesday = anchor(2026, 8, 19)
        assertEquals("2026-08-17", WeekRange.mondayOf(0, wednesday))
        assertEquals("2026-08-23", WeekRange.sundayOf(0, wednesday))
        assertEquals("2026-08-24", WeekRange.mondayOf(1, wednesday))
        assertEquals("2026-08-30", WeekRange.sundayOf(1, wednesday))
    }

    @Test
    fun `Saturday anchor still belongs to the week that started Monday`() {
        val saturday = anchor(2026, 8, 15)
        assertEquals("2026-08-10", WeekRange.mondayOf(0, saturday))
        assertEquals("2026-08-16", WeekRange.sundayOf(0, saturday))
        assertEquals("2026-08-17", WeekRange.mondayOf(1, saturday))
        assertEquals("2026-08-23", WeekRange.sundayOf(1, saturday))
    }

    /** The case Calendar.set(DAY_OF_WEEK, MONDAY) gets wrong under a Sunday-first locale. */
    @Test
    fun `Sunday anchor resolves back to the preceding Monday not the upcoming one`() {
        val sunday = anchor(2026, 8, 16)
        assertEquals("2026-08-10", WeekRange.mondayOf(0, sunday))
        assertEquals("2026-08-16", WeekRange.sundayOf(0, sunday))
        assertEquals("2026-08-17", WeekRange.mondayOf(1, sunday))
        assertEquals("2026-08-23", WeekRange.sundayOf(1, sunday))
    }

    @Test
    fun `Sunday anchor is unaffected by a Sunday-first locale`() {
        val sunday = Calendar.getInstance(vilnius, Locale.US).apply {
            firstDayOfWeek = Calendar.SUNDAY
            clear()
            set(2026, Calendar.AUGUST, 16, 12, 0, 0)
        }
        assertEquals("2026-08-10", WeekRange.mondayOf(0, sunday))
        assertEquals("2026-08-16", WeekRange.sundayOf(0, sunday))
    }

    // --- boundaries ----------------------------------------------------------------------

    @Test
    fun `week spanning a month boundary`() {
        val wednesday = anchor(2026, 9, 2)
        assertEquals("2026-08-31", WeekRange.mondayOf(0, wednesday))
        assertEquals("2026-09-06", WeekRange.sundayOf(0, wednesday))
    }

    @Test
    fun `week spanning a year boundary`() {
        val thursday = anchor(2026, 12, 31)
        assertEquals("2026-12-28", WeekRange.mondayOf(0, thursday))
        assertEquals("2027-01-03", WeekRange.sundayOf(0, thursday))
        assertEquals("2027-01-04", WeekRange.mondayOf(1, thursday))
        assertEquals("2027-01-10", WeekRange.sundayOf(1, thursday))
    }

    // --- DST ------------------------------------------------------------------------------
    // Europe/Vilnius springs forward 2026-03-29 03:00 and falls back 2026-10-25 04:00.
    // Anchored at midnight, where a naive 7 * 24h millisecond hop drifts into the wrong date.

    @Test
    fun `stepping forward across the autumn fall-back does not lose a day`() {
        // 2026-10-25 has 25 hours, so Mon 00:00 + 168h would land on Sunday 23:00.
        val monday = anchor(2026, 10, 19, hour = 0)
        assertEquals("2026-10-19", WeekRange.mondayOf(0, monday))
        assertEquals("2026-10-25", WeekRange.sundayOf(0, monday))
        assertEquals("2026-10-26", WeekRange.mondayOf(1, monday))
        assertEquals("2026-11-01", WeekRange.sundayOf(1, monday))
    }

    @Test
    fun `stepping forward across the spring-forward does not gain a day`() {
        // 2026-03-29 has 23 hours.
        val monday = anchor(2026, 3, 23, hour = 0)
        assertEquals("2026-03-23", WeekRange.mondayOf(0, monday))
        assertEquals("2026-03-29", WeekRange.sundayOf(0, monday))
        assertEquals("2026-03-30", WeekRange.mondayOf(1, monday))
        assertEquals("2026-04-05", WeekRange.sundayOf(1, monday))
    }

    @Test
    fun `the fall-back Sunday itself resolves to its own week`() {
        val sunday = anchor(2026, 10, 25, hour = 0, minute = 30)
        assertEquals("2026-10-19", WeekRange.mondayOf(0, sunday))
        assertEquals("2026-10-25", WeekRange.sundayOf(0, sunday))
    }

    @Test
    fun `the spring-forward Sunday itself resolves to its own week`() {
        val sunday = anchor(2026, 3, 29, hour = 12)
        assertEquals("2026-03-23", WeekRange.mondayOf(0, sunday))
        assertEquals("2026-03-29", WeekRange.sundayOf(0, sunday))
    }

    @Test
    fun `stepping backward across the fall-back does not lose a day`() {
        val monday = anchor(2026, 10, 26, hour = 0)
        assertEquals("2026-10-19", WeekRange.mondayOf(-1, monday))
        assertEquals("2026-10-25", WeekRange.sundayOf(-1, monday))
    }

    // --- defaultOffset ---------------------------------------------------------------------

    @Test
    fun `defaultOffset is 1 on the weekend`() {
        assertEquals(1, WeekRange.defaultOffset(anchor(2026, 8, 15))) // Saturday
        assertEquals(1, WeekRange.defaultOffset(anchor(2026, 8, 16))) // Sunday
    }

    @Test
    fun `defaultOffset is 0 on weekdays`() {
        assertEquals(0, WeekRange.defaultOffset(anchor(2026, 8, 17))) // Monday
        assertEquals(0, WeekRange.defaultOffset(anchor(2026, 8, 18))) // Tuesday
        assertEquals(0, WeekRange.defaultOffset(anchor(2026, 8, 19))) // Wednesday
        assertEquals(0, WeekRange.defaultOffset(anchor(2026, 8, 20))) // Thursday
        assertEquals(0, WeekRange.defaultOffset(anchor(2026, 8, 21))) // Friday
    }

    // --- public no-arg delegates ------------------------------------------------------------

    @Test
    fun `no-arg delegates produce well formed output`() {
        val iso = Regex("""\d{4}-\d{2}-\d{2}""")
        assertTrue(iso.matches(WeekRange.mondayOf(0)))
        assertTrue(iso.matches(WeekRange.sundayOf(0)))
        assertTrue(WeekRange.defaultOffset() in 0..1)
    }

    /**
     * Sweeps every day of 2026 (both DST transitions included) asserting the invariants:
     * the start is a Monday, the end is a Sunday six days later, and the anchor day itself
     * falls inside the range that will be used as the yyyy-MM-dd filter.
     */
    @Test
    fun `every day of the year lands in a Monday to Sunday range containing it`() {
        val cal = anchor(2026, 1, 1, hour = 0)
        repeat(365) {
            val today = isoOf(cal)
            val monday = WeekRange.mondayOf(0, cal)
            val sunday = WeekRange.sundayOf(0, cal)

            val mondayCal = parseIso(monday)
            assertEquals("$monday should be a Monday", Calendar.MONDAY, dayOfWeek(mondayCal))

            val sundayCal = parseIso(sunday)
            assertEquals("$sunday should be a Sunday", Calendar.SUNDAY, dayOfWeek(sundayCal))

            mondayCal.add(Calendar.DAY_OF_YEAR, 6)
            assertEquals("$sunday should be $monday plus six days", sunday, isoOf(mondayCal))

            assertTrue("$today should fall within $monday..$sunday", today in monday..sunday)

            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun dayOfWeek(cal: Calendar) = cal.get(Calendar.DAY_OF_WEEK)

    private fun isoOf(cal: Calendar) = String.format(
        Locale.US,
        "%04d-%02d-%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )

    private fun parseIso(iso: String) = anchor(
        iso.substring(0, 4).toInt(),
        iso.substring(5, 7).toInt(),
        iso.substring(8, 10).toInt(),
        hour = 0
    )
}
