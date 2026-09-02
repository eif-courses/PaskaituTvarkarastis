package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import android.content.Context
import android.provider.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Stand-in lectures and changes for exercising the change-highlight path on demand.
 *
 * Debug source set only: the release variant has its own DebugFixtures that always returns
 * null, so none of this data is compiled into a release build.
 *
 * Everything is dated relative to today, so the rows land in the currently displayed week
 * whenever the fixture is switched on, and survive a "today forward" filter.
 *
 * Flip it on with:
 *   adb shell run-as eif.viko.lt.appsas.paskaitutvarkarastis touch files/use-fixture-lectures
 * and off with:
 *   adb shell run-as eif.viko.lt.appsas.paskaitutvarkarastis rm files/use-fixture-lectures
 */
object DebugFixtures {

    private const val PREF_KEY = "useFixtureLectures"
    private const val MARKER_FILE = "use-fixture-lectures"

    fun isEnabled(context: Context): Boolean {
        val bySetting = runCatching {
            Settings.Global.getInt(context.contentResolver, PREF_KEY, 0)
        }.getOrDefault(0) != 0

        // Newer Android builds stop an app reading its own custom Settings.Global key, where
        // the call simply returns the default instead of failing. A marker file in the app's
        // own storage is not subject to that, so it works as a second switch.
        val byMarker = runCatching {
            File(context.filesDir, MARKER_FILE).exists()
        }.getOrDefault(false)

        return bySetting || byMarker
    }

    /** Null unless the fixture is switched on, so callers fall back to the API result. */
    fun lecturesOrNull(context: Context): List<LecturesDto>? =
        if (isEnabled(context)) lectures() else null

    /** A sync time 13h ago, so the caption shows its stale (date) form on demand. */
    fun syncedAtOrNull(context: Context): Long? =
        if (isEnabled(context)) System.currentTimeMillis() - 13L * 60 * 60 * 1000 else null

    /** Changes that line up with [lecturesOrNull], so ChangeMatcher has something to match. */
    fun changesOrNull(context: Context): List<ChangeDto>? =
        if (isEnabled(context)) changes() else null

    private fun today(): Calendar = Calendar.getInstance()

    private fun yesterday(): Calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    private fun iso(c: Calendar): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)

    /**
     * "HH:mm" a given number of minutes from now, clamped to today, so the rows are always
     * finished / in progress / still ahead relative to the moment the widget is looked at.
     */
    private fun hhmm(deltaMinutes: Int): String {
        val c = Calendar.getInstance()
        val minutes = (c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE) + deltaMinutes)
            .coerceIn(0, 23 * 60 + 59)
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)
    }

    /** The JavaScript Date.prototype.toDateString() form the real feed uses. */
    private fun js(c: Calendar): String = SimpleDateFormat("EEE MMM d yyyy", Locale.US).format(c.time)

    // Three rows on one day so a single screenshot shows every state side by side:
    // cancelled, moved, and an untouched hybrid row for the online icon.
    private fun lectures(): List<LecturesDto> {
        val d = iso(today())
        return listOf(
            // Dated yesterday: in the current week this row must be trimmed away, so its
            // absence is the visible proof that "today forward" is working.
            LecturesDto(
                date = iso(yesterday()),
                uniperiod = "1",
                classids = listOf("IS25"),
                subjectid = "Testinis dalykas (vakar)",
                classroomids = listOf("104"),
                starttime = "08:30",
                endtime = "10:00",
                teacherids = listOf("M. Gžegoževskis")
            ),
            // Finished, nothing else wrong with it: plain muting.
            LecturesDto(
                date = d,
                uniperiod = "1",
                classids = listOf("EI26"),
                subjectid = "Testinis dalykas (baigta)",
                classroomids = listOf("201"),
                starttime = hhmm(-300),
                endtime = hhmm(-210),
                teacherids = listOf("M. Gžegoževskis")
            ),
            // Cancelled AND finished: the case where two mute treatments could stack into
            // illegibility. Kept in the fixture permanently rather than covered by reasoning.
            LecturesDto(
                date = d,
                uniperiod = "6",
                classids = listOf("PI24"),
                subjectid = "Testinis dalykas (atšaukta, baigta)",
                classroomids = listOf("202"),
                starttime = hhmm(-240),
                endtime = hhmm(-150),
                teacherids = listOf("M. Gžegoževskis")
            ),
            // Cancelled DURING its own slot: the precedence case. It must show no
            // in-progress tint - the lecture is not happening, so a tint would be a lie.
            LecturesDto(
                date = d,
                uniperiod = "2",
                classids = listOf("PI23SN"),
                subjectid = "Testinis dalykas (atšaukta)",
                classroomids = listOf("101"),
                starttime = hhmm(-40),
                endtime = hhmm(+50),
                teacherids = listOf("M. Gžegoževskis")
            ),
            // In progress right now and otherwise unremarkable: the tint on its own.
            LecturesDto(
                date = d,
                uniperiod = "5",
                classids = listOf("IS26"),
                subjectid = "Testinis dalykas (vyksta)",
                classroomids = listOf("305"),
                starttime = hhmm(-25),
                endtime = hhmm(+35),
                teacherids = listOf("M. Gžegoževskis")
            ),
            // In progress right now, and moved: full contrast.
            LecturesDto(
                date = d,
                uniperiod = "3",
                classids = listOf("KS25"),
                subjectid = "Testinis dalykas (perkelta)",
                classroomids = listOf("102"),
                starttime = hhmm(-30),
                endtime = hhmm(+45),
                teacherids = listOf("M. Gžegoževskis")
            ),
            // Still ahead, hybrid room for the online icon.
            LecturesDto(
                date = d,
                uniperiod = "4",
                classids = listOf("IS25"),
                subjectid = "Testinis dalykas (įprasta)",
                classroomids = listOf("103", "MS Teams"),
                starttime = hhmm(+60),
                endtime = hhmm(+150),
                teacherids = listOf("M. Gžegoževskis")
            )
        )
    }

    private fun changes(): List<ChangeDto> {
        val d = js(today())
        return listOf(
            ChangeDto(
                date = d,
                paskaita = "6",
                grupe = "<b>PI24</b>",
                auditorija = "-",
                destytojas = "Paskaitos nėra"
            ),
            // Cancelled the way the department really files it: marker without the
            // diacritic, and the stale room left in place.
            ChangeDto(
                date = d,
                paskaita = "2",
                grupe = "<b>PI23SN</b>",
                auditorija = "101",
                destytojas = "Paskaitos nera"
            ),
            // Moved to another room.
            ChangeDto(
                date = d,
                paskaita = "3",
                grupe = "<b>KS25</b>",
                auditorija = "320",
                destytojas = "M. Gžegoževskis"
            )
        )
    }
}
