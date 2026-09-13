package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as UnitColorProvider

/**
 * The widget's fixed palette: cool tonal neutrals, one pair per role, light / dark.
 *
 * Fixed rather than Material You on purpose: the status colours (cancelled, moved) have to
 * hold their meaning against the surface on every wallpaper, and a dynamic surface made
 * that impossible to guarantee. Dark values are derived with the same relationships as
 * light - lighter and less saturated - not inverted.
 *
 * Structural things this file does not own: bar widths, spacing, the time column, the
 * font-scale gates, the nested row bounds.
 */
object WidgetPalette {

    private fun pair(day: Long, night: Long): UnitColorProvider = ColorProvider(Color(day), Color(night))

    /** Card background. */
    val surface = pair(0xFFFBFCFD, 0xFF16181C)

    /** Subject, start time, header primary line, icon buttons. */
    val primaryText = pair(0xFF171A1F, 0xFFE6E8EC)

    /** Meta line, day header, teacher line, caption, list-body states. */
    val secondaryText = pair(0xFF5D6470, 0xFFA3A9B4)

    /**
     * End time, day summary. Text read every day, so it clears 4.4:1 light / 4.5:1 dark
     * while staying visibly quieter than [secondaryText] (1.30:1 / 1.66:1 between them).
     */
    val mutedText = pair(0xFF6F7683, 0xFF7A818E)

    /** The accent bar when nothing has changed: quiet, so the exception bars out-shout it. */
    val neutralBar = pair(0xFFDCE0E6, 0xFF343943)

    /** Cancelled: the bar, the "Cancelled" line, and the failed-refresh caption. */
    val cancelled = pair(0xFFC2413F, 0xFFE88582)

    /**
     * Moved: the bar and the "→ Room" line, which carries the room the reader is walking
     * to, so the light value clears 4.5:1. Same luminance as cancelled red on purpose -
     * the two are told apart by hue (35° amber vs 3° red), by the strikethrough, and by
     * the line's own words; not by one being darker.
     */
    val moved = pair(0xFF9E6210, 0xFFEFAF52)

    /** In-progress row wash. Unchanged from the original tuning. */
    val inProgressTint = pair(0xFFE9E6EE, 0xFF2B2A30)

    /** Online pill: fill and text, fully rounded, no border. */
    val pillFill = pair(0xFFE2ECF7, 0xFF1E2F45)
    val pillText = pair(0xFF24527F, 0xFF9CC4EE)

    /** "today" pill shares the pill pair, so the two chips read as one family. */
    val todayFill = pillFill
    val todayText = pillText

    /** The entity name link in the header: the pill's blue, the one saturated neutral. */
    val link = pillText

    /** A row's resting fill: fully transparent, so the rounded block is invisible at rest. */
    val rowRest: UnitColorProvider = UnitColorProvider(Color.Transparent)
}
