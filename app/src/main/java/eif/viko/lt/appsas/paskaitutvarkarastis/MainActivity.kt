package eif.viko.lt.appsas.paskaitutvarkarastis

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaskaituTvarkarastisTheme {
                ConfigurationActivity()
            }
        }
    }

    @Composable
    fun CustomListItem(entity: EntityDto, onListItemClicked: () -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp)
                .clickable(onClick = onListItemClicked),
            elevation = CardDefaults.cardElevation(10.dp)
        ) {
            Column(
                modifier = Modifier.padding(15.dp)
            ) {
                Text(entity.label)
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

        val uiScope = rememberCoroutineScope()

        LaunchedEffect(mode) {
            selected = null
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
                        text = { Text(candidate.label) }
                    )
                }
            }

            val chosen = selected
            when {
                loading -> Message("Kraunama...")

                failed -> Message("Programėlė neveikia arba nėra interneto :)")

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
                        TextButton(onClick = { selected = null }) { Text("Atgal") }
                    }
                    if (lectures.isEmpty()) {
                        Message("Šiuo metu paskaitų nėra")
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

                entities.isEmpty() -> Message("Sąrašas tuščias")

                else -> LazyColumn {
                    itemsIndexed(entities) { _, entity ->
                        CustomListItem(entity = entity) {
                            // The pair is stored for every mode; TEACHER_ID stays alongside
                            // it so the widget keeps working exactly as before.
                            coroutineScope.launch {
                                mainDataStorage.writeString("ENTITY_TYPE", mode.name)
                                mainDataStorage.writeString("ENTITY_ID", entity.id)
                            }

                            // Configuring a widget: bind it and hand control back to the
                            // launcher. Showing a screen here would leave the widget
                            // unconfigured, which is not what the user asked for.
                            if (configuredWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                bindEntity(mode, entity)
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
    private fun bindEntity(mode: PickerMode, entity: EntityDto) {
        coroutineScope.launch {
            // Only teachers touch this: it is the fallback for widgets placed before they had
            // an entity of their own, and a group id there would be read back as a teacher id.
            if (mode == PickerMode.TEACHER) {
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

            targets.forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[TimetableWidget.entityTypeKey] = mode.name
                    prefs[TimetableWidget.entityIdKey] = entity.id
                    // The label is the selected entity's, not something derived from a
                    // lecture payload, so it is known even out of term.
                    prefs[TimetableWidget.teacherNameKey] = entity.label
                }
                TimetableWidget.update(context, glanceId)
            }
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
            "Paskauskite atnaujinti, kad matytumėte tvarkaraščio pakeitimus!",
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
