package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddedLecturesTest {

    /** The real Friday 10-16 for PI26: Matematika at period 3 in the feed, two records. */
    private val feed = listOf(
        LecturesDto(date = "2026-10-16", uniperiod = "3", starttime = "12:30", endtime = "14:00",
            subjectid = "Matematika", classids = listOf("PI26"), teacherids = listOf("Doc. L. Gžegoževskė"))
    )
    private val cancelled = ChangeDto(date = "Fri Oct 16 2026", paskaita = "3", destytojas = "Paskaitos nėra", auditorija = "-", grupe = "<b>PI26</b>")
    private val moved = ChangeDto(date = "Fri Oct 16 2026", paskaita = "2", destytojas = "dr. L. Gžegoževskė", auditorija = "222", grupe = "<b>PI26</b>")

    @Test
    fun `a named record in an empty slot becomes a new lecture for the teacher`() {
        val rows = AddedLectures.synthesize(listOf(cancelled, moved), feed, PickerMode.TEACHER, "Doc. L. Gžegoževskė")
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(AddedLectures.TYPE, row.type)
        assertEquals("2026-10-16", row.date)
        assertEquals("2", row.uniperiod)
        assertEquals("10:15" to "11:45", row.starttime to row.endtime)
        assertEquals("L. Gžegoževskė", row.subjectid)
        assertEquals(listOf("PI26"), row.classids)
        assertEquals(listOf("222"), row.classroomids)
        assertTrue(row.teacherids.isEmpty())
    }

    @Test
    fun `and for the group`() {
        val rows = AddedLectures.synthesize(listOf(cancelled, moved), feed, PickerMode.GROUP, "PI26 I pogr.")
        assertEquals(1, rows.size)
        assertEquals("2026-10-16", rows.single().date)
    }

    @Test
    fun `another teacher's or group's record is not ours`() {
        assertTrue(AddedLectures.synthesize(listOf(moved), feed, PickerMode.TEACHER, "Lekt. M. Gžegoževskis").isEmpty())
        assertTrue(AddedLectures.synthesize(listOf(moved), feed, PickerMode.GROUP, "PI24").isEmpty())
        assertTrue(AddedLectures.synthesize(listOf(moved), feed, PickerMode.CLASSROOM, "222").isEmpty())
    }

    /** A named record on an existing lecture is a room change, which the row itself shows. */
    @Test
    fun `a record on an occupied slot is not synthesised`() {
        val roomChange = moved.copy(paskaita = "3", auditorija = "320")
        assertTrue(AddedLectures.synthesize(listOf(roomChange), feed, PickerMode.TEACHER, "Doc. L. Gžegoževskė").isEmpty())
    }

    @Test
    fun `cancellations and unplaceable periods are skipped`() {
        assertTrue(AddedLectures.synthesize(listOf(cancelled), feed, PickerMode.GROUP, "PI26").isEmpty())
        assertTrue(AddedLectures.synthesize(listOf(moved.copy(paskaita = "")), feed, PickerMode.GROUP, "PI26").isEmpty())
        assertTrue(AddedLectures.synthesize(listOf(moved.copy(paskaita = "9")), feed, PickerMode.GROUP, "PI26").isEmpty())
    }

    @Test
    fun `a subgroup in the record lands in groupnames so the filter applies`() {
        val sub = moved.copy(grupe = "<b>PI26 II pogrupis</b>")
        val row = AddedLectures.synthesize(listOf(sub), feed, PickerMode.GROUP, "PI26").single()
        assertEquals(listOf("II pogr."), row.groupnames)
        assertTrue(SubgroupFilter.matches(row, "II pogr."))
        assertFalse(SubgroupFilter.matches(row, "I pogr."))
    }

    @Test
    fun `teacher names match across titles and case`() {
        assertTrue(TeacherName.same("dr. L. Gžegoževskė", "Doc. L. Gžegoževskė"))
        assertTrue(TeacherName.same("L. Gžegoževskė ", "Lekt.  L. Gžegoževskė"))
        assertFalse(TeacherName.same("L. Gžegoževskė", "M. Gžegoževskis"))
        assertFalse(TeacherName.same("", ""))
    }

    @Test
    fun `title stripping is case-insensitive and knows dr`() {
        assertEquals("L. Gžegoževskė", "dr. L. Gžegoževskė".withoutAcademicTitle())
        assertEquals("M. Gžegoževskis", "Lekt. M. Gžegoževskis".withoutAcademicTitle())
        assertEquals("A. Kiškė", "A. Kiškė".withoutAcademicTitle())
    }

    /** The real PI26 Friday: Kubilinskienė already teaches period 2. The record naming
     *  Gžegoževskė at period 2 is her lecture, not a room change of Kubilinskienė's. */
    @Test
    fun `a named record on another teacher's lecture is an added lecture, not a room change`() {
        val other = LecturesDto(date = "2026-10-16", uniperiod = "2", starttime = "10:15", endtime = "11:45",
            subjectid = "Žiniatinklio kūrimo pagrindai", classids = listOf("PI26"), classroomids = listOf("320"),
            teacherids = listOf("Doc. S. Kubilinskienė"))
        assertEquals(null, ChangeMatcher.findFor(other, listOf(moved)))
        val rows = AddedLectures.synthesize(listOf(moved), feed + other, PickerMode.GROUP, "PI26")
        assertEquals(1, rows.size)
        assertEquals("L. Gžegoževskė", rows.single().subjectid)
    }

    /** But a record naming the slot's own teacher is still a room change on that row. */
    @Test
    fun `a named record on the teacher's own lecture stays a room change`() {
        val own = feed.single()
        val roomChange = moved.copy(paskaita = "3", auditorija = "320")
        assertEquals(roomChange, ChangeMatcher.findFor(own, listOf(roomChange)))
        assertTrue(AddedLectures.synthesize(listOf(roomChange), feed, PickerMode.GROUP, "PI26").isEmpty())
        // and a cancellation applies whoever is named on the lecture
        assertEquals(cancelled, ChangeMatcher.findFor(own, listOf(cancelled)))
    }
}
