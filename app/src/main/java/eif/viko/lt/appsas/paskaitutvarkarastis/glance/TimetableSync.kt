package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.google.gson.Gson
import eif.viko.lt.appsas.paskaitutvarkarastis.MainDataStorage

/** What a sync managed to do, so callers can decide whether retrying is worthwhile. */
enum class SyncOutcome {
    /** Everything fetched. The timetable may still legitimately be empty. */
    SUCCESS,

    /** Lectures stored, but Firebase changes could not be fetched. */
    CHANGES_UNAVAILABLE,

    /** The timetable endpoint failed, so nothing was stored. */
    TIMETABLE_UNAVAILABLE,

    /** No teacher picked yet, so there is nothing to fetch. */
    NO_TEACHER_SELECTED
}

/**
 * The single fetch-and-store path, shared by the refresh button and the periodic worker.
 *
 * Clients come from [HttpClients] rather than being rebuilt per call: a widget refresh used
 * to construct a fresh Retrofit (and therefore a fresh connection pool and thread pool) on
 * every tap, and the worker would repeat that every 15 minutes.
 */
object TimetableSync {

    private const val TAG = "TimetableSync"

    private val timetableService: TimetableApi get() = HttpClients.timetableApi

    private val firebaseService: FirebaseApi get() = HttpClients.firebaseApi

    private val gson = Gson()

    suspend fun syncTimetable(context: Context): SyncOutcome {
        val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(TimetableWidget::class.java)

        // Widgets placed before per-widget selection existed carry no entity of their own.
        val storedTeacherId = MainDataStorage.getInstance(context).readString("TEACHER_ID")

        // Resolved up front, because whether Firebase is worth asking at all depends on what
        // the widgets are bound to. Widgets with nothing picked yet drop out here.
        val bindings = glanceIds.mapNotNull { glanceId ->
            val state = runCatching {
                getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
            }.getOrNull()

            val entityType = PickerMode.fromNameOrTeacher(state?.get(TimetableWidget.entityTypeKey))
            val entityId = state?.get(TimetableWidget.entityIdKey)?.takeIf { it.isNotBlank() }
                ?: storedTeacherId

            if (entityId.isNullOrBlank()) null else Triple(glanceId, entityType, entityId)
        }

        if (bindings.isEmpty()) return SyncOutcome.NO_TEACHER_SELECTED

        // Changes are entity independent and small, so one fetch is shared by every widget.
        // A classroom timetable never highlights them, so a classroom-only setup skips the
        // request entirely. The timetable must still render when Firebase is unreachable.
        val changesResult = if (bindings.any { it.second != PickerMode.CLASSROOM }) {
            runCatching { firebaseService.getChanges().values.toList() }
        } else {
            null
        }
        changesResult?.exceptionOrNull()?.let { Log.w(TAG, "Firebase changes fetch failed", it) }
        val changes: List<ChangeDto> = changesResult?.getOrDefault(emptyList()) ?: emptyList()
        val changesJson = gson.toJson(changes)

        var failed = 0

        bindings.forEach { (glanceId, entityType, entityId) ->
            // An empty list is a normal answer out of term time, not a failure: it gets
            // stored and rendered like any other result. Only a thrown call counts.
            val timetable = runCatching {
                fetchLectures(entityType, entityId) to timetableService.getCurrentWeek()
            }.getOrElse {
                Log.w(TAG, "Timetable fetch failed for $entityType $entityId", it)
                failed++
                return@forEach
            }

            val (fetchedLectures, currentWeek) = timetable

            // Debug-only stand-in while the endpoint is out of term. It substitutes for an
            // empty result only, and feeds the same store-and-render path as real data, so
            // ChangeMatcher still does the matching. Release builds have no fixture.
            val lectures = if (fetchedLectures.isEmpty()) {
                DebugFixtures.lecturesOrNull(context) ?: fetchedLectures
            } else {
                fetchedLectures
            }

            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[TimetableWidget.countKey] = gson.toJson(lectures)
                prefs[TimetableWidget.additionalDataKey] = gson.toJson(currentWeek)
                prefs[TimetableWidget.changesKey] = changesJson
                // teacherNameKey is deliberately not written here: the label is the selected
                // entity's, stored at selection time, and deriving one from lecture payloads
                // would overwrite it with whatever teacherids happens to contain.
            }
        }

        return when {
            // Retry only when every widget failed; a partial failure still put something on
            // screen, so re-fetching for all of them would spend battery for little gain.
            failed == bindings.size -> SyncOutcome.TIMETABLE_UNAVAILABLE
            changesResult?.isFailure == true -> SyncOutcome.CHANGES_UNAVAILABLE
            else -> SyncOutcome.SUCCESS
        }
    }

    private suspend fun fetchLectures(entityType: PickerMode, entityId: String) =
        when (entityType) {
            PickerMode.TEACHER -> timetableService.getLectures(entityId)
            PickerMode.GROUP -> timetableService.getGroupLectures(entityId)
            PickerMode.CLASSROOM -> timetableService.getClassroomLectures(entityId)
        }
}
