package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.ParseException
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background

import androidx.glance.Image
import androidx.glance.appwidget.ImageProvider

import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentWidth
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextDefaults
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.google.gson.Gson
import eif.viko.lt.appsas.paskaitutvarkarastis.MainActivity
import eif.viko.lt.appsas.paskaitutvarkarastis.MainDataStorage
import eif.viko.lt.appsas.paskaitutvarkarastis.R
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

object TimetableWidget : GlanceAppWidget() {

    val countKey = stringPreferencesKey("count")
    val additionalDataKey = stringPreferencesKey("additionalData")
    val changesKey = stringPreferencesKey("changes")
    val teacherNameKey = stringPreferencesKey("teacherName")

    // Which entity this particular widget shows. Absent on widgets placed before per-widget
    // selection existed; those fall back to TEACHER plus MainDataStorage's TEACHER_ID, so no
    // migration step is needed.
    val entityTypeKey = stringPreferencesKey("entityType")
    val entityIdKey = stringPreferencesKey("entityId")


    //    var selectedDateIndex by remember { mutableStateOf(0) }
    val dateIndexKey = intPreferencesKey("dateIndex")

    @RequiresApi(Build.VERSION_CODES.O)
    @Composable
    fun Content(
        context: Context,
        storedTeacherName: String = "",
        appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    ) {
        val count = currentState(key = countKey) ?: "nera paskaitų"
        val additionalDataJson = currentState(key = additionalDataKey) ?: ""
        val changesJson = currentState(key = changesKey) ?: ""

        // Per-widget state first so a live widget reflects a fresh pick immediately, then
        // the stored name, which a widget placed after the pick is the only way to know it.
        val teacherName = currentState(key = teacherNameKey)?.takeIf { it.isNotBlank() }
            ?: storedTeacherName.takeIf { it.isNotBlank() }
            ?: "Pasirinkite dėstytoją"

        //val dateIndex = currentState(key = dateIndexKey) ?: 1
        val gson = Gson()


        val safeData = if (additionalDataJson.isNotBlank()) {
            gson.fromJson(additionalDataJson, Int::class.java) ?: 0
        } else {
            0  // Default value if the JSON string is null or empty
        }

        // currentweek is an even/odd parity flag, not an ordinal week number, so it is
        // rendered as I or II rather than raw. Any future offset must be folded back in as
        // (currentWeek + offset) % 2 to stay inside {0, 1}; the extra + 2 keeps a negative
        // value from producing -1.
        val weekLabel = if (((safeData % 2) + 2) % 2 == 0) "I" else "II"

        // A change record names a group, so a lecture moving OUT of a room cannot be
        // represented in a classroom timetable; highlighting there would assert something the
        // data does not support. Mirrors the in-app view's highlight = false.
        val highlightChanges =
            PickerMode.fromNameOrTeacher(currentState(key = entityTypeKey)) != PickerMode.CLASSROOM

        val changes: List<ChangeDto> = if (changesJson.isNotBlank()) {
            runCatching {
                gson.fromJson(changesJson, Array<ChangeDto>::class.java)?.toList().orEmpty()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(Color(0, 81, 255, 0xBB)).padding(top = 10.dp),
//            verticalAlignment = Alignment.Vertical.CenterVertically,
//            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {


            Row {


                Spacer(modifier = GlanceModifier.width(10.dp)) // Add some spacing between the button and LazyColumn

//                Button(
//                    text = "Naujinti",
//                    onClick = actionRunCallback(IncrementActionCallback::class.java),
//                    modifier = GlanceModifier.padding(7.dp).wrapContentWidth()
//                )


                Image(
                    provider = androidx.glance.ImageProvider(R.drawable.baseline_refresh_24), // Replace with your drawable
                    contentDescription = "Refresh",
                    modifier = GlanceModifier
                        .size(48.dp)
                        .clickable(actionRunCallback(IncrementActionCallback::class.java))
                )




                //Spacer(modifier = GlanceModifier.width(5.dp)) // Add some spacing between the button and LazyColumn

                Text(
                    text = teacherName,
                    GlanceModifier.padding(start = 30.dp, top = 10.dp).clickable {
                        openMainActivity(context = context, appWidgetId = appWidgetId)
                    },
                    style = TextStyle(
                        textAlign = TextAlign.Center,
                        color = ColorProvider(Color(236, 247, 255, 0xFF))
                    )
                )

                Text(
                    text = "($weekLabel sav.)",
                    GlanceModifier.padding(start = 30.dp, top = 10.dp),
                    style = TextStyle(
                        textAlign = TextAlign.Center,
                        color = ColorProvider(Color(236, 247, 255, 0xFF))
                    )
                )


            }
            val locale = Locale.forLanguageTag("lt-LT")

            if (count != "nera paskaitų") {
                val lecturesDtos = gson.fromJson(count, Array<LecturesDto>::class.java)

                val dateFormat = "yyyy-MM-dd" // Format of the date string
                // val dayOfWeek = getDayOfWeek(dateString)
                LazyColumn {
                    for (day in DayOfWeek.values()) {
                        val lecturesForDay = lecturesDtos.filter {

                            val dayOfWeek = getDayOfWeekFromString(it.date, dateFormat)
                            dayOfWeek == day
                            //dayOfWeek == day && day == DayOfWeek.valueOf(LocalDate.now().toString())
                        }


                        //val dayOfWeek = getDayOfWeekFromString(it.date, dateFormat)
                        val containsDate = lecturesDtos.any {
                            getDayOfWeekFromString(it.date, dateFormat) == day
                        }
                        if (containsDate) {


                            val dayHeader = day.getDisplayName(
                                java.time.format.TextStyle.FULL,
                                locale
                            )
                                .toString()
                                .replaceFirstChar {
                                    if (it.isLowerCase()) it.titlecase(
                                        Locale.ROOT
                                    ) else it.toString()
                                }

                            val today = LocalDate.now()

                            // val testDate = LocalDate.parse("2023-09-04")

                            if (today.dayOfWeek == day) {

                                item {
                                    Text(
                                        text = "$dayHeader (Šiandien)",
                                        GlanceModifier
                                            .fillMaxSize()
                                            .padding(4.dp),
                                        style = TextStyle(
                                            fontSize = 20.sp, textAlign = TextAlign.Center,
                                            color = ColorProvider(Color.Yellow)
                                        )
                                    )
                                }
                            } else {

                                item {
                                    Text(
                                        text = dayHeader,
                                        GlanceModifier
                                            .fillMaxSize()
                                            .padding(4.dp),
                                        style = TextStyle(
                                            fontSize = 20.sp, textAlign = TextAlign.Center,
                                            color = ColorProvider(Color(236, 247, 255, 0xFF))
                                        )
                                    )
                                }
                            }

                        }


                        // Print date header
//                        Text(
//                            text = day.toString(),
//                            modifier = GlanceModifier.padding(top = 8.dp)
//                        )

                        // Print lectures for the current day
                        itemsIndexed(lecturesForDay) { index, lecture ->

                            val change = if (highlightChanges) {
                                ChangeMatcher.findFor(lecture, changes)
                            } else {
                                null
                            }
                            val status = change?.let { ChangeMatcher.statusOf(it) }

                            val rowBackground = when (status) {
                                ChangeStatus.CANCELLED -> Color(0xFFDC2626)
                                ChangeStatus.CHANGED -> Color(0xFFFACC15)
                                null -> Color(236, 247, 255, 0xFF)
                            }
                            val textColor = when (status) {
                                ChangeStatus.CANCELLED -> ColorProvider(Color.White)
                                ChangeStatus.CHANGED -> ColorProvider(Color.Black)
                                null -> TextDefaults.defaultTextColor
                            }
                            // The blue classroom accent is unreadable on the red/yellow
                            // backgrounds, so it yields to the status colour.
                            val accentColor = if (status == null) {
                                ColorProvider(Color(0, 81, 255, 0xBB))
                            } else {
                                textColor
                            }

                            Row(
                                modifier = GlanceModifier.padding(2.dp)
                                    .background(color = rowBackground),
                            ) {

                                Column(
                                    modifier = GlanceModifier
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.Vertical.CenterVertically,
                                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                                ) {
                                    Spacer(GlanceModifier.padding(1.dp).background(Color.Gray))
                                    Text(
                                        text = lecture.subjectid + ", " + lecture.groupnames.joinToString(
                                            ", "
                                        ),
                                        style = TextStyle(
                                            color = textColor,
                                            fontSize = 16.sp,
                                            textDecoration = if (status == ChangeStatus.CANCELLED) {
                                                TextDecoration.LineThrough
                                            } else {
                                                TextDecoration.None
                                            }
                                        )
                                    )


                                    Row {
                                        Text(
                                            text = lecture.classids.joinToString(", "),
                                            style = TextStyle(color = textColor, fontSize = 16.sp)
                                        )
                                        // A cancelled lecture has no room to go to, and the
                                        // stored one would just be misleading.
                                        if (status != ChangeStatus.CANCELLED) {
                                            Text(
                                                text = " (" + lecture.classroomids.joinToString(", ") + " aud.)",
                                                style = TextStyle(
                                                    color = accentColor,
                                                    fontSize = 16.sp, fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }

                                    Text(
                                        text = lecture.uniperiod + " paskaita, " + lecture.starttime + "-" + lecture.endtime + " val.",
                                        style = TextStyle(
                                            color = textColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )

                                    if (status == ChangeStatus.CANCELLED) {
                                        // Fixed label, not the destytojas field: on a
                                        // cancellation that field holds the marker rather
                                        // than a teacher, and echoing it would read as one.
                                        Text(
                                            text = ChangeMatcher.CANCELLED_MARKER,
                                            style = TextStyle(
                                                color = textColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    } else if (change != null && status == ChangeStatus.CHANGED) {
                                        Text(
                                            text = "→ " + change.auditorija.trim() + " aud., " + change.destytojas.trim(),
                                            style = TextStyle(
                                                color = textColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    /**
     * Carries the widget id along so the picker can bind the choice to this widget alone
     * rather than to every placed widget.
     */
    fun openMainActivity(
        context: Context,
        appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    ) {
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = FLAG_ACTIVITY_NEW_TASK
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        context.startActivity(intent)
    }


    // TODO need upgrade version to minimal api 23 instead 26
    @RequiresApi(Build.VERSION_CODES.O)
    fun getDayOfWeekFromString(dateString: String, dateFormat: String): DayOfWeek? {
        try {
            val sdf = SimpleDateFormat(dateFormat, Locale.US)
            val date = sdf.parse(dateString)
            val sdf2 = SimpleDateFormat("EEEE", Locale.US)
            val dayOfWeekString = sdf2.format(date)

            return when (dayOfWeekString) {
                "Monday" -> DayOfWeek.MONDAY
                "Tuesday" -> DayOfWeek.TUESDAY
                "Wednesday" -> DayOfWeek.WEDNESDAY
                "Thursday" -> DayOfWeek.THURSDAY
                "Friday" -> DayOfWeek.FRIDAY
                "Saturday" -> DayOfWeek.SATURDAY
                "Sunday" -> DayOfWeek.SUNDAY
                else -> null // Invalid day of the week string
            }
        } catch (e: ParseException) {
            e.printStackTrace()
            return null // Invalid date string
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read here rather than in Content: MainDataStorage is suspend-only and composition
        // must not block.
        val storedTeacherName = runCatching {
            MainDataStorage.getInstance(context).readString("TEACHER_NAME")
        }.getOrNull().orEmpty()

        val appWidgetId = runCatching {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        }.getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)

        provideContent {
            Content(context, storedTeacherName, appWidgetId)
        }
    }


}


class TimetableMyReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = TimetableWidget


}

// TODO fix callback he***l state update
class SelectPreviousDateActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, glanceId) { prefs ->
            val currentDate = prefs[TimetableWidget.dateIndexKey]
            if ((currentDate != null) && (currentDate > 0)) {
                prefs[TimetableWidget.dateIndexKey] = currentDate - 1
            } else {
                prefs[TimetableWidget.dateIndexKey] = 1
            }
        }
        TimetableWidget.update(context, glanceId)
    }
}

class SelectNextDateActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, glanceId) { prefs ->
            val currentDate = prefs[TimetableWidget.dateIndexKey]
            if (currentDate != null) { // TODO get lecturesDtos.size-1
                prefs[TimetableWidget.dateIndexKey] = currentDate + 1
            } else {
                prefs[TimetableWidget.dateIndexKey] = 1
            }
        }
        TimetableWidget.update(context, glanceId)
    }
}


class IncrementActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Same fetch-and-store path the periodic worker uses.
        TimetableSync.syncTimetable(context)
        TimetableWidget.update(context, glanceId)
    }
}