package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FontScaleTest {

    /**
     * The threshold is a magic number by nature; this pins it so a change to it is a
     * deliberate edit here, not a drift.
     */
    @Test
    fun `large text starts strictly above 1 point 3`() {
        assertFalse(FontScale.isLargeText(1.0f))    // default
        assertFalse(FontScale.isLargeText(1.15f))   // one step up, still single-line
        assertFalse(FontScale.isLargeText(1.3f))    // the boundary itself is still normal
        assertTrue(FontScale.isLargeText(1.31f))
        assertTrue(FontScale.isLargeText(1.5f))
        assertTrue(FontScale.isLargeText(2.0f))     // the maximum tested on device
    }
}
