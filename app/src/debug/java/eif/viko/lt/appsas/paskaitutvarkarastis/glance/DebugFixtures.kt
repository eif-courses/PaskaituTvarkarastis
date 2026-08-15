package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import android.content.Context
import android.provider.Settings

/**
 * Stand-in lectures for exercising the change-highlight path while /timetable/teacher is
 * out of term and returns [].
 *
 * Debug source set only: the release variant has its own DebugFixtures that always returns
 * null, so none of this data is compiled into a release build.
 *
 * Flip it on with:
 *   adb shell settings put global useFixtureLectures 1
 * and off with:
 *   adb shell settings put global useFixtureLectures 0
 */
object DebugFixtures {

    private const val PREF_KEY = "useFixtureLectures"

    /** Null unless the pref is on, so callers fall back to whatever the API returned. */
    fun lecturesOrNull(context: Context): List<LecturesDto>? {
        val enabled = runCatching {
            Settings.Global.getInt(context.contentResolver, PREF_KEY, 0)
        }.getOrDefault(0) != 0

        return if (enabled) FIXTURE_LECTURES else null
    }

    // The first two line up with the two confirmed user-posts records, so ChangeMatcher
    // should mark them CHANGED. The third has no counterpart in Firebase and must stay
    // unhighlighted, giving a visible contrast row.
    private val FIXTURE_LECTURES = listOf(
        LecturesDto(
            date = "2026-08-17",
            uniperiod = "1",
            classids = listOf("PI23SN"),
            groupnames = emptyList(),
            subjectid = "Testinis dalykas",
            classroomids = listOf("101"),
            starttime = "08:30",
            endtime = "10:00",
            teacherids = listOf("M. Gžegoževskis")
        ),
        LecturesDto(
            date = "2026-08-18",
            uniperiod = "2",
            classids = listOf("KS25"),
            groupnames = emptyList(),
            subjectid = "Testinis dalykas 2",
            classroomids = listOf("102"),
            starttime = "10:15",
            endtime = "11:45",
            teacherids = listOf("M. Gžegoževskis")
        ),
        LecturesDto(
            date = "2026-08-19",
            uniperiod = "3",
            classids = listOf("IS25"),
            groupnames = emptyList(),
            subjectid = "Testinis dalykas 3",
            classroomids = listOf("103"),
            starttime = "12:00",
            endtime = "13:30",
            teacherids = listOf("M. Gžegoževskis")
        )
    )
}
