package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import android.content.Context

/**
 * Release counterpart of the debug fixture provider. Always null, and carries none of the
 * fixture data, so the stand-in lectures cannot reach a release build at all.
 */
object DebugFixtures {

    fun isEnabled(context: Context): Boolean = false

    fun lecturesOrNull(context: Context): List<LecturesDto>? = null

    fun changesOrNull(context: Context): List<ChangeDto>? = null

    fun syncedAtOrNull(context: Context): Long? = null
}
