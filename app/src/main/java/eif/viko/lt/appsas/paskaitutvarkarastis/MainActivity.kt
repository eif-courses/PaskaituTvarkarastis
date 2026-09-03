package eif.viko.lt.appsas.paskaitutvarkarastis

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import eif.viko.lt.appsas.paskaitutvarkarastis.glance.ChangeDto
import eif.viko.lt.appsas.paskaitutvarkarastis.glance.ChangeMatcher
import eif.viko.lt.appsas.paskaitutvarkarastis.glance.ChangeStatus
import eif.viko.lt.appsas.paskaitutvarkarastis.glance.EntityDto
import eif.viko.lt.appsas.paskaitutvarkarastis.glance.FirebaseApi
import eif.viko.lt.appsas.paskaitutvarkarastis.glance.HttpClients
import eif.viko.lt.appsas.paskaitutvarkarastis.glance.LecturesDto
import eif.viko.lt.appsas.paskaitutvarkarastis.glance.PickerMode
import eif.viko.lt.appsas.paskaitutvarkarastis.glance.TimetableApi
import eif.viko.lt.appsas.paskaitutvarkarastis.glance.TimetableSync
import eif.viko.lt.appsas.paskaitutvarkarastis.glance.WeekRange
import eif.viko.lt.appsas.paskaitutvarkarastis.glance.TimetableWidget
import eif.viko.lt.appsas.paskaitutvarkarastis.ui.theme.PaskaituTvarkarastisTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val mainDataStorage = MainDataStorage.getInstance(this)
    val coroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * Set when launched as a widget configuration activity, or when the widget header was
     * tapped. INVALID when opened from the launcher, where the choice applies to every widget.
     */
    private val configuredWidgetId: Int by lazy {
        intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private val service: TimetableApi get() = HttpClients.timetableApi

    private val firebaseService: FirebaseApi get() = HttpClients.firebaseApi

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Either answer is fine: without it the widget still updates, changes just stay silent.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContent {
            PaskaituTvarkarastisTheme {
                ConfigurationActivity()
            }
        }
    }

    companion object {
        /** Logcat tag for every write of the entity selection. */
        const val BINDING_TAG = "EntityBinding"
        const val EXTRA_ENTITY_TYPE = "entityType"
        const val EXTRA_ENTITY_ID = "entityId"
        const val EXTRA_ENTITY_LABEL = "entityLabel"

        /** Passed by a lesson tap. Nothing reads it yet; a day-anchored view can later. */
    }

    /** Only Android 13+ gates notifications behind a runtime permission. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Composable
    fun CustomListItem(entity: EntityDto, onListItemClicked: () -> Unit) {
        LabelCard(label = entity.label, onClick = onListItemClicked)
    }

    @Composable
    private fun LabelCard(label: String, onClick: () -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp)
                .clickable(onClick = onClick),
            elevation = CardDefaults.cardElevation(10.dp)
        ) {
            Column(
                modifier = Modifier.padding(15.dp)
            ) {
                Text(label)
            }
        }
    }

    @Composable
    fun ConfigurationActivity() {

        var mode by remember { mutableStateOf(PickerMode.TEACHER) }
        var entities by remember { mutableStateOf<List<EntityDto>>(emptyList()) }
        var selected by remember { mutableStateOf<EntityDto?>(null) }
        var lectures by remember { mutableStateOf<List<LecturesDto>>(emptyList()) }
        var changes by remember { mutableStateOf<List<ChangeDto>>(emptyList()) }
        var loading by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }

        // Set once a group is picked while configuring a widget, holding the second step open
        // until a subgroup is chosen.
        var pendingGroup by remember { mutableStateOf<EntityDto?>(null) }
        var subgroupOptions by remember { mutableStateOf<List<String>>(emptyList()) }

        val uiScope = rememberCoroutineScope()

        LaunchedEffect(mode) {
            selected = null
            pendingGroup = null
            subgroupOptions = emptyList()
            lectures = emptyList()
            failed = false
            loading = true
            entities = runCatching {
                when (mode) {
                    PickerMode.TEACHER -> service.getTeachersIds()
                    PickerMode.GROUP -> service.getGroupIds()
                    PickerMode.CLASSROOM -> service.getClassroomIds()
                }
            }.getOrElse {
                failed = true
                emptyList()
            }
            loading = false
        }

        Column(modifier = Modifier.fillMaxSize()) {

            TabRow(selectedTabIndex = mode.ordinal) {
                PickerMode.values().forEach { candidate ->
                    Tab(
                        selected = candidate == mode,
                        onClick = { mode = candidate },
                        text = { Text(stringResource(candidate.labelRes)) }
                    )
                }
            }

            val chosen = selected
            val pending = pendingGroup
            when {
                loading -> Message(stringResource(R.string.widget_loading))

                failed -> Message(stringResource(R.string.picker_failed))

                pending != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.picker_subgroup_title, pending.label),
                            modifier = Modifier.padding(vertical = 12.dp),
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { pendingGroup = null }) { Text(stringResource(R.string.picker_back)) }
                    }
                    LazyColumn {
                        item {
                            LabelCard(stringResource(R.string.picker_all_subgroups)) { bindEntity(PickerMode.GROUP, pending) }
                        }
                        items(subgroupOptions) { subgroup ->
                            LabelCard(subgroup) {
                                bindEntity(PickerMode.GROUP, pending, subgroup)
                            }
                        }
                    }
                }

                chosen != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = chosen.label,
                            modifier = Modifier.padding(vertical = 12.dp),
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { selected = null }) { Text(stringResource(R.string.picker_back)) }
                    }
                    if (lectures.isEmpty()) {
                        Message(stringResource(R.string.picker_no_lectures))
                    } else {
                        // Classroom changes carry different semantics, so highlighting stays
                        // off for that mode until it is designed properly.
                        TimetableList(
                            lectures = lectures,
                            changes = changes,
                            highlight = mode != PickerMode.CLASSROOM
                        )
                    }
                }

                entities.isEmpty() -> Message(stringResource(R.string.picker_empty_list))

                else -> LazyColumn {
                    itemsIndexed(entities) { _, entity ->
                        CustomListItem(entity = entity) {
                            // The pair is stored for every mode; TEACHER_ID stays alongside
                            // it so the widget keeps working exactly as before.
                            coroutineScope.launch {
                                // Every write of the selection is logged: widget 6 once
                                // changed entity with no picker interaction anyone could
                                // account for, so the next time it happens the log says who.
                                Log.i(
                                    BINDING_TAG,
                                    "selection store <- ${mode.name} ${entity.id} '${entity.label}' " +
                                        "(launcher list, configuredWidgetId=$configuredWidgetId)"
                                )
                                mainDataStorage.writeString("ENTITY_TYPE", mode.name)
                                mainDataStorage.writeString("ENTITY_ID", entity.id)
                                // Read back by the sync to label notification lines, and to
                                // notify at all for someone who never placed a widget.
                                mainDataStorage.writeString("ENTITY_LABEL", entity.label)
                            }

                            // Configuring a widget: bind it and hand control back to the
                            // launcher. Showing a screen here would leave the widget
                            // unconfigured, which is not what the user asked for.
                            if (configuredWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                if (mode == PickerMode.GROUP) {
                                    // Subgroups are not an endpoint of their own: they exist
                                    // only on the group's lectures, so they must be fetched
                                    // before they can be offered.
                                    uiScope.launch {
                                        loading = true
                                        val options = runCatching {
                                            service.getGroupLectures(entity.id)
                                        }.getOrDefault(emptyList())
                                            .flatMap { it.groupnames }
                                            .map { ChangeMatcher.normalizeGroup(it) }
                                            .filter { it.isNotBlank() }
                                            .distinct()
                                            .sorted()
                                        loading = false

                                        if (options.isEmpty()) {
                                            // Nothing to choose between, so bind unfiltered
                                            // rather than strand the user on an empty list.
                                            bindEntity(mode, entity)
                                        } else {
                                            subgroupOptions = options
                                            pendingGroup = entity
                                        }
                                    }
                                } else {
                                    bindEntity(mode, entity)
                                }
                                return@CustomListItem
                            }

                            when (mode) {
                                PickerMode.TEACHER -> bindEntity(mode, entity)

                                PickerMode.GROUP, PickerMode.CLASSROOM -> {
                                    selected = entity
                                    uiScope.launch {
                                        loading = true
                                        lectures = runCatching {
                                            if (mode == PickerMode.GROUP) {
                                                service.getGroupLectures(entity.id)
                                            } else {
                                                service.getClassroomLectures(entity.id)
                                            }
                                        }.getOrElse {
                                            failed = true
                                            emptyList()
                                        }
                                        changes = if (mode == PickerMode.GROUP) {
                                            runCatching {
                                                firebaseService.getChanges().values.toList()
                                            }.getOrDefault(emptyList())
                                        } else {
                                            emptyList()
                                        }
                                        loading = false
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Binds a widget to the chosen entity, then hands control back to whoever launched us. */
    private fun bindEntity(mode: PickerMode, entity: EntityDto, subgroup: String = "") {
        coroutineScope.launch {
            // Only teachers touch this: it is the fallback for widgets placed before they had
            // an entity of their own, and a group id there would be read back as a teacher id.
            if (mode == PickerMode.TEACHER) {
                Log.i(BINDING_TAG, "teacher fallback <- ${entity.id} '${entity.label}'")
                mainDataStorage.writeString("TEACHER_ID", entity.id)
                mainDataStorage.writeString("TEACHER_NAME", entity.label)
            }

            val context = applicationContext
            val manager = GlanceAppWidgetManager(context)

            // Bound to the launching widget when there is one, so two widgets can show two
            // different entities. Falls back to every widget when opened from the launcher.
            val targets = if (configuredWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                runCatching { listOf(manager.getGlanceIdBy(configuredWidgetId)) }
                    .getOrDefault(emptyList())
            } else {
                manager.getGlanceIds(TimetableWidget::class.java)
            }

            Log.i(
                BINDING_TAG,
                "widget state <- ${mode.name} ${entity.id} '${entity.label}' subgroup='$subgroup' " +
                    "targets=$targets configuredWidgetId=$configuredWidgetId " +
                    "caller=${Throwable().stackTrace.getOrNull(1)?.let { "${it.methodName}:${it.lineNumber}" }}"
            )
            targets.forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[TimetableWidget.entityTypeKey] = mode.name
                    prefs[TimetableWidget.entityIdKey] = entity.id
                    // Always written, so a subgroup from an earlier binding cannot linger on
                    // a widget that has since been pointed at something else.
                    prefs[TimetableWidget.subgroupKey] = subgroup
                    // A fresh pick starts on the current week: carrying over a week the user
                    // had paged to for some other entity would just be confusing.
                    prefs[TimetableWidget.weekOffsetKey] = 0
                    // The label is the selected entity's, not something derived from a
                    // lecture payload, so it is known even out of term. The subgroup rides
                    // along so an active filter is visible in the header.
                    prefs[TimetableWidget.teacherNameKey] =
                        if (subgroup.isBlank()) entity.label else "${entity.label} $subgroup"
                }
            }

            // Fetch straight away, so the widget shows the chosen timetable instead of the
            // previous one until the user thinks to tap refresh. Failure is not fatal here:
            // the binding is already stored and the next sync will fill it in.
            runCatching { TimetableSync.syncTimetable(context) }

            targets.forEach { glanceId -> TimetableWidget.update(context, glanceId) }
        }

        // The configuration contract: the launcher only keeps the widget if it gets RESULT_OK
        // with the id back.
        if (configuredWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, configuredWidgetId)
            )
        }

        Toast.makeText(
            this@MainActivity,
            getString(R.string.picker_bound_toast),
            Toast.LENGTH_LONG
        ).show()
        finish()
    }

    @Composable
    private fun Message(text: String) {
        Text(text = text, modifier = Modifier.padding(16.dp))
    }

    @Composable
    private fun TimetableList(
        lectures: List<LecturesDto>,
        changes: List<ChangeDto>,
        highlight: Boolean
    ) {
        LazyColumn {
            lectures.sortedBy { it.date }.groupBy { it.date }.forEach { (date, dayLectures) ->
                item {
                    Text(
                        text = date,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(dayLectures) { lecture ->
                    LectureRow(
                        lecture = lecture,
                        change = if (highlight) ChangeMatcher.findFor(lecture, changes) else null
                    )
                }
            }
        }
    }

    @Composable
    private fun LectureRow(lecture: LecturesDto, change: ChangeDto?) {
        val status = change?.let { ChangeMatcher.statusOf(it) }

        val background = when (status) {
            ChangeStatus.CANCELLED -> Color(0xFFDC2626)
            ChangeStatus.CHANGED -> Color(0xFFFACC15)
            null -> MaterialTheme.colorScheme.surfaceVariant
        }
        val foreground = when (status) {
            ChangeStatus.CANCELLED -> Color.White
            ChangeStatus.CHANGED -> Color.Black
            null -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .background(background)
                .padding(12.dp)
        ) {
            Text(
                text = listOfNotNull(
                    lecture.subjectid.ifBlank { null },
                    lecture.groupnames.joinToString(", ").ifBlank { null }
                ).joinToString(", "),
                color = foreground,
                fontSize = 16.sp,
                textDecoration = if (status == ChangeStatus.CANCELLED) {
                    TextDecoration.LineThrough
                } else {
                    TextDecoration.None
                }
            )
            Text(
                text = lecture.classids.joinToString(", ") +
                    // A cancelled lecture has no room to go to.
                    if (status == ChangeStatus.CANCELLED) {
                        ""
                    } else {
                        " (" + lecture.classroomids.joinToString(", ") + " aud.)"
                    },
                color = foreground,
                fontSize = 16.sp
            )
            Text(
                text = lecture.uniperiod + " paskaita, " +
                    lecture.starttime + "-" + lecture.endtime + " val.",
                color = foreground,
                fontWeight = FontWeight.Bold
            )
            if (status == ChangeStatus.CANCELLED) {
                Text(
                    text = ChangeMatcher.CANCELLED_MARKER,
                    color = foreground,
                    fontWeight = FontWeight.Bold
                )
            } else if (change != null && status == ChangeStatus.CHANGED) {
                Text(
                    text = "→ " + change.auditorija.trim() + " aud., " + change.destytojas.trim(),
                    color = foreground,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
