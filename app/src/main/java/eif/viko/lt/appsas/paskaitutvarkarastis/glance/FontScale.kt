package eif.viko.lt.appsas.paskaitutvarkarastis.glance

/**
 * The one place the widget makes a decision based on the system font scale.
 *
 * Glance cannot measure text, so it cannot know whether a line will fit. Above this scale
 * single-line text that fits at 1.0 reliably does not, and the lines that carry the most
 * information - the header date range and the meta line - are allowed to wrap to two lines
 * rather than ellipsize. Below it nothing changes, which is what keeps 1.0 rendering
 * byte-for-byte identical to before this rule existed.
 */
object FontScale {

    /** Exclusive: 1.3 itself is still "normal". Pinned by FontScaleTest. */
    const val LARGE_TEXT_THRESHOLD = 1.3f

    fun isLargeText(fontScale: Float): Boolean = fontScale > LARGE_TEXT_THRESHOLD
}
