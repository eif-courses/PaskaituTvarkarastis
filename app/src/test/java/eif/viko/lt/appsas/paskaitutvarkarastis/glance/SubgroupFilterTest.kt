package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubgroupFilterTest {

    private fun lecture(vararg groupnames: String) =
        LecturesDto(classids = listOf("PI23E"), groupnames = groupnames.toList())

    @Test
    fun `a blank filter keeps everything`() {
        assertTrue(SubgroupFilter.matches(lecture("I pogr."), ""))
        assertTrue(SubgroupFilter.matches(lecture("II pogr."), "   "))
        assertTrue(SubgroupFilter.matches(lecture(), ""))
    }

    /** The rule that keeps a filtered widget useful: shared lectures must not vanish. */
    @Test
    fun `a whole-group lecture survives any filter`() {
        assertTrue(SubgroupFilter.matches(lecture(), "I pogr."))
        assertTrue(SubgroupFilter.matches(lecture("  "), "II pogr."))
    }

    @Test
    fun `a lecture for the selected subgroup is kept`() {
        assertTrue(SubgroupFilter.matches(lecture("I pogr."), "I pogr."))
    }

    @Test
    fun `a lecture for another subgroup is dropped`() {
        assertFalse(SubgroupFilter.matches(lecture("II pogr."), "I pogr."))
        assertFalse(SubgroupFilter.matches(lecture("1 pogr."), "2 pogr."))
    }

    @Test
    fun `a lecture listing several subgroups is kept when one of them matches`() {
        assertTrue(SubgroupFilter.matches(lecture("I pogr.", "II pogr."), "II pogr."))
        assertFalse(SubgroupFilter.matches(lecture("I pogr.", "II pogr."), "III pogr."))
    }

    /** Both sides go through normalizeGroup, so spelling variants line up. */
    @Test
    fun `spelling variants of the same subgroup match`() {
        assertTrue(SubgroupFilter.matches(lecture("II pogrupis"), "II pogr."))
        assertTrue(SubgroupFilter.matches(lecture("(II pogr.)"), "II pogr."))
        assertTrue(SubgroupFilter.matches(lecture("<b>II pogr.</b>"), "II pogr."))
        assertTrue(SubgroupFilter.matches(lecture("II  pogr."), "II pogr."))
        assertTrue(SubgroupFilter.matches(lecture("ii pogr."), "II pogr."))
    }

    @Test
    fun `a near miss is not treated as a match`() {
        assertFalse(SubgroupFilter.matches(lecture("III pogr."), "II pogr."))
        assertFalse(SubgroupFilter.matches(lecture("II"), "II pogr."))
    }
}
