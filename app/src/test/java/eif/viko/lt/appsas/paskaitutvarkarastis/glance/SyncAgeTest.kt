package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SyncAgeTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(TimeZone.getDefault()).apply {
            clear(); set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private val hour = 60L * 60 * 1000

    /** Old enough, but still today: the time is the more precise fact and is kept. */
    @Test
    fun `a same-day sync keeps its time however old`() {
        val synced = at(2026, 9, 2, 6, 0)
        assertFalse(SyncAge.isStale(synced, at(2026, 9, 2, 18, 1)))   // 12h + 1min, same day
        assertFalse(SyncAge.isStale(synced, at(2026, 9, 2, 23, 59)))  // nearly 18h, same day
        assertEquals("06:00", SyncAge.label(synced, at(2026, 9, 2, 23, 59)))
    }

    /** Both conditions: past twelve hours AND the calendar has turned over. */
    @Test
    fun `twelve hours across midnight is the boundary and it is exclusive`() {
        val synced = at(2026, 9, 1, 22, 0)
        assertFalse(SyncAge.isStale(synced, synced + 12 * hour))     // exactly 12h: not yet
        assertTrue(SyncAge.isStale(synced, synced + 12 * hour + 1))  // 10:00:00.001 next day
        assertEquals("09-01", SyncAge.label(synced, at(2026, 9, 2, 10, 1)))
    }

    @Test
    fun `fresh shows the time and stale shows the date`() {
        val synced = at(2026, 9, 1, 9, 30)
        assertEquals("09:30", SyncAge.label(synced, synced + 3 * hour))
        assertEquals("09-01", SyncAge.label(synced, at(2026, 9, 2, 8, 0)))
    }

    /** A sync yesterday evening looked at this morning: under 12h, so still a time. */
    @Test
    fun `an overnight gap under twelve hours keeps the time form`() {
        val synced = at(2026, 9, 1, 22, 0)
        assertEquals("22:00", SyncAge.label(synced, at(2026, 9, 2, 7, 0)))
    }
}
