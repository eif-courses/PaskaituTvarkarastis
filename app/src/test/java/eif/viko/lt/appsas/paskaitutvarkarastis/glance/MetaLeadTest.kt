package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetaLeadTest {

    private fun lecture(vararg groupnames: String, classids: List<String> = listOf("IS26")) =
        LecturesDto(classids = classids, groupnames = groupnames.toList())

    /** The defect being fixed: two parallel subgroup lectures must be distinguishable. */
    @Test
    fun `group binding leads with the subgroup label`() {
        assertEquals("I pogr.", MetaLead.of(lecture("I pogr."), PickerMode.GROUP))
        assertEquals("II pogr.", MetaLead.of(lecture("II pogr."), PickerMode.GROUP))
    }

    /** Labels vary per programme; whatever the feed carries is rendered as-is. */
    @Test
    fun `group binding renders any label verbatim`() {
        assertEquals("FIN", MetaLead.of(lecture("FIN"), PickerMode.GROUP))
        assertEquals("DB", MetaLead.of(lecture(" DB "), PickerMode.GROUP))
        assertEquals("I pogr., II pogr.", MetaLead.of(lecture("I pogr.", "II pogr."), PickerMode.GROUP))
    }

    /** Decided edge case: a whole-group lecture gets no lead segment, not the group code. */
    @Test
    fun `group binding with no subgroup has no lead segment`() {
        assertNull(MetaLead.of(lecture(), PickerMode.GROUP))
        // The feed sends [""] rather than [] for these.
        assertNull(MetaLead.of(lecture(""), PickerMode.GROUP))
        assertNull(MetaLead.of(lecture("  "), PickerMode.GROUP))
    }

    @Test
    fun `group binding never shows the group code`() {
        assertNull(MetaLead.of(lecture(classids = listOf("IS26", "IS26E")), PickerMode.GROUP))
    }

    /** The signed-off paths: group codes exactly as before, subgroup labels ignored. */
    @Test
    fun `teacher and classroom bindings lead with the group codes unchanged`() {
        val shared = lecture("I pogr.", classids = listOf("PI24E", "PI24"))
        assertEquals("PI24E, PI24", MetaLead.of(shared, PickerMode.TEACHER))
        assertEquals("PI24E, PI24", MetaLead.of(shared, PickerMode.CLASSROOM))
        assertNull(MetaLead.of(lecture(classids = emptyList()), PickerMode.TEACHER))
    }
}
