package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
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

    /** Something to fetch for. A null [glanceId] means notifications only, with no widget. */
    private data class SyncTarget(
        val glanceId: GlanceId?,
        val entityType: PickerMode,
        val entityId: String,
        val label: String,
        /** Which week this target displays. 0 is the week containing today. */
        val weekOffset: Int
    )

    suspend fun syncTimetable(context: Context): SyncOutcome {
        val storage = MainDataStorage.getInstance(context)
        val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(TimetableWidget::class.java)

        // Widgets placed before per-widget selection existed carry no entity of their own.
        val storedTeacherId = storage.readString("TEACHER_ID")

        // Resolved up front, because whether Firebase is worth asking at all depends on what
        // the targets are. Widgets with nothing picked yet drop out here.
        val widgetTargets = glanceIds.mapNotNull { glanceId ->
            val state = runCatching {
                getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
            }.getOrNull()

            val entityType = PickerMode.fromNameOrTeacher(state?.get(TimetableWidget.entityTypeKey))
            val entityId = state?.get(TimetableWidget.entityIdKey)?.takeIf { it.isNotBlank() }
                ?: storedTeacherId
            val label = state?.get(TimetableWidget.teacherNameKey).orEmpty()

            val weekOffset = TimetableWidget.effectiveWeekOffset(
                state?.get(TimetableWidget.weekOffsetKey) ?: 0
            )

            if (entityId.isNullOrBlank()) {
                null
            } else {
                SyncTarget(glanceId, entityType, entityId, label, weekOffset)
            }
        }

        // The widgets decide what is watched. The picker's own selection is only a fallback
        // for someone who has placed no widget at all: otherwise merely browsing another
        // group in the app would quietly start notifying about it, which is not what
        // "the widget is watching this group" means.
        //
        // Classrooms never take part: a change record names a group, so it cannot say
        // anything about a room, and asking for one would only waste a request.
        val selectedType = PickerMode.fromNameOrTeacher(storage.readString("ENTITY_TYPE"))
        val selectedId = storage.readString("ENTITY_ID")
        val selectionTarget = if (
            widgetTargets.isEmpty() &&
            !selectedId.isNullOrBlank() &&
            selectedType != PickerMode.CLASSROOM
        ) {
            SyncTarget(
                glanceId = null,
                entityType = selectedType,
                entityId = selectedId,
                label = storage.readString("ENTITY_LABEL").orEmpty(),
                weekOffset = TimetableWidget.effectiveWeekOffset(0)
            )
        } else {
            null
        }

        val targets = widgetTargets + listOfNotNull(selectionTarget)
        if (targets.isEmpty()) return SyncOutcome.NO_TEACHER_SELECTED

        // Changes are entity independent and small, so one fetch is shared by every target.
        // A classroom timetable never highlights them, so a classroom-only setup skips the
        // request entirely. The timetable must still render when Firebase is unreachable.
        val changesResult = if (targets.any { it.entityType != PickerMode.CLASSROOM }) {
            runCatching { firebaseService.getChanges().values.toList() }
        } else {
            null
        }
        changesResult?.exceptionOrNull()?.let { Log.w(TAG, "Firebase changes fetch failed", it) }
        // Debug-only stand-in that lines up with the fixture lectures, so ChangeMatcher has
        // something to match on demand. Release builds have no fixture to return.
        val changes: List<ChangeDto> = DebugFixtures.changesOrNull(context)
            ?: changesResult?.getOrDefault(emptyList())
            ?: emptyList()
        val changesJson = gson.toJson(changes)

        var failed = 0
        val matched = mutableListOf<MatchedChange>()
        // Stamped on the widgets that actually get new data, so the caption cannot claim a
        // fresher time than the timetable it sits above. The debug fixture can supply an
        // old value so the stale form of the caption can be seen without waiting 12h.
        val syncedAt = DebugFixtures.syncedAtOrNull(context) ?: System.currentTimeMillis()
        // Keyed by entity and week, so the notification pass below can reuse a week the
        // display loop already fetched instead of asking for it twice.
        val fetchedWeeks = mutableMapOf<Triple<PickerMode, String, Int>, List<LecturesDto>>()

        targets.forEach { target ->
            val (glanceId, entityType, entityId) = target

            // An empty list is a normal answer out of term time, not a failure: it gets
            // stored and rendered like any other result. Only a thrown call counts.
            val timetable = runCatching {
                fetchWeek(entityType, entityId, target.weekOffset) to
                    timetableService.getCurrentWeek()
            }.getOrElse {
                Log.w(TAG, "Timetable fetch failed for $entityType $entityId", it)
                failed++
                // Only the flag is written. The cached week is left exactly as it was, so a
                // failed refresh never blanks the widget.
                if (glanceId != null) {
                    updateAppWidgetState(context, glanceId) { prefs ->
                        prefs[TimetableWidget.lastSyncFailedKey] = true
                        prefs[TimetableWidget.refreshingKey] = false
                    }
                }
                return@forEach
            }

            val (fetchedLectures, currentWeek) = timetable

            // Debug-only stand-in. Now that every entity has term data, "only when empty"
            // would make the fixture unreachable, so the switch simply wins when it is on.
            // It feeds the same store-and-render path as real data, so ChangeMatcher still
            // does the matching. Release builds have no fixture to return.
            val lectures = DebugFixtures.lecturesOrNull(context) ?: fetchedLectures

            fetchedWeeks[Triple(entityType, entityId, target.weekOffset)] = lectures

            // A notification-only target has no widget state to write.
            if (glanceId == null) return@forEach

            // The endpoint is still fetched and stored, but the header no longer reads it.
            // A disagreement is logged so a stale anchor (new term) or a flipped Firebase
            // node is visible in logcat instead of silently rendering the wrong parity.
            val derived = WeekParity.of(WeekRange.mondayOf(0))
            if (currentWeek != derived) {
                Log.w(
                    TAG,
                    "Parity disagreement for the week of ${WeekRange.mondayOf(0)}: " +
                        "endpoint=$currentWeek derived=$derived (anchor ${WeekParity.ANCHOR_MONDAY}=" +
                        "${WeekParity.ANCHOR_PARITY}). Check WeekParity if a new term started."
                )
            }

            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[TimetableWidget.countKey] = gson.toJson(lectures)
                prefs[TimetableWidget.additionalDataKey] = gson.toJson(currentWeek)
                prefs[TimetableWidget.changesKey] = changesJson
                prefs[TimetableWidget.lastSyncedAtKey] = syncedAt
                prefs[TimetableWidget.refreshingKey] = false
                prefs[TimetableWidget.lastSyncFailedKey] = false
                // teacherNameKey is deliberately not written here: the label is the selected
                // entity's, stored at selection time, and deriving one from lecture payloads
                // would overwrite it with whatever teacherids happens to contain.
            }
        }

        // Notifications track the week that is actually coming up, not whichever week the
        // user has paged the widget to: leaving a widget on week +2 must not silence changes
        // to tomorrow's lectures. Usually the display loop already fetched it, so this costs
        // an extra request only while a widget is parked on another week.
        val notifyOffset = TimetableWidget.effectiveWeekOffset(0)
        targets.asSequence()
            .filter { it.entityType != PickerMode.CLASSROOM }
            .distinctBy { it.entityType to it.entityId }
            .forEach { target ->
                val key = Triple(target.entityType, target.entityId, notifyOffset)
                val lectures = fetchedWeeks[key] ?: runCatching {
                    fetchWeek(target.entityType, target.entityId, notifyOffset)
                }.getOrElse {
                    Log.w(TAG, "Notification week fetch failed for ${target.entityId}", it)
                    emptyList()
                }
                lectures.forEach { lecture ->
                    ChangeMatcher.findFor(lecture, changes)?.let { change ->
                        matched += MatchedChange(lecture, change, target.label)
                    }
                }
            }

        // Never allowed to affect the sync outcome: a notification problem must not make the
        // worker retry a fetch that already succeeded.
        val boundEntities = targets.distinctBy { it.entityType to it.entityId }.size
        runCatching { ChangeNotifier.notifyNewMatches(context, matched, boundEntities) }
            .onFailure { Log.w(TAG, "Could not raise change notification", it) }

        return when {
            // Retry only when every widget failed; a partial failure still put something on
            // screen, so re-fetching for all of them would spend battery for little gain.
            failed == targets.size -> SyncOutcome.TIMETABLE_UNAVAILABLE
            changesResult?.isFailure == true -> SyncOutcome.CHANGES_UNAVAILABLE
            else -> SyncOutcome.SUCCESS
        }
    }

    /** Asks the backend for exactly one Monday-to-Sunday week, computed locally. */
    private suspend fun fetchWeek(
        entityType: PickerMode,
        entityId: String,
        weekOffset: Int
    ): List<LecturesDto> {
        val from = WeekRange.mondayOf(weekOffset)
        val to = WeekRange.sundayOf(weekOffset)

        return when (entityType) {
            PickerMode.TEACHER -> timetableService.getLectures(entityId, from, to)
            PickerMode.GROUP -> timetableService.getGroupLectures(entityId, from, to)
            PickerMode.CLASSROOM -> timetableService.getClassroomLectures(entityId, from, to)
        }
    }
}
