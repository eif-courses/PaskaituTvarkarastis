package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.ParseException
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentWidth
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.google.gson.Gson
import eif.viko.lt.appsas.paskaitutvarkarastis.MainActivity
import eif.viko.lt.appsas.paskaitutvarkarastis.MainDataStorage
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

object TimetableWidget : GlanceAppWidget() {

    val countKey = stringPreferencesKey("count")


    //    var selectedDateIndex by remember { mutableStateOf(0) }
    val dateIndexKey = intPreferencesKey("dateIndex")

    @RequiresApi(Build.VERSION_CODES.O)
    @Composable
    fun Content(context: Context) {
        val count = currentState(key = countKey) ?: "nera paskaitų"

        var teacherName by remember {
            mutableStateOf("Pasirinkite dėstytoją")
        }

        //val dateIndex = currentState(key = dateIndexKey) ?: 1
        val gson = Gson()

        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(Color(0, 81, 255, 0xBB)).padding(top = 10.dp),
//            verticalAlignment = Alignment.Vertical.CenterVertically,
//            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {


            Row {

                Spacer(modifier = GlanceModifier.width(10.dp)) // Add some spacing between the button and LazyColumn

                Button(
                    text = "Naujinti",
                    onClick = actionRunCallback(IncrementActionCallback::class.java),
                    modifier = GlanceModifier.padding(7.dp).wrapContentWidth()
                )
                //Spacer(modifier = GlanceModifier.width(5.dp)) // Add some spacing between the button and LazyColumn

                Text(
                    text = teacherName,
                    GlanceModifier.padding(start = 30.dp, top = 10.dp).clickable {
                        openMainActivity(context = context)
                    },
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


                            teacherName =
                                lecture.teacherids.toString().replace("[", "")
                                    .replace("]", "")
                            //name = lecture.teacherids.toString()


                            Row(
                                modifier = GlanceModifier.padding(2.dp)
                                    .background(color = Color(236, 247, 255, 0xFF)),
                            ) {

                                Column(
                                    modifier = GlanceModifier
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.Vertical.CenterVertically,
                                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                                ) {
                                    Spacer(GlanceModifier.padding(1.dp).background(Color.Gray))
                                    Text(
                                        text = lecture.subjectid + ", " + lecture.groupnames.toString()
                                            .replace("[", "").replace("]", ""),
                                        style = TextStyle(fontSize = 16.sp)
                                    )


                                    Row {
                                        Text(
                                            text = lecture.classids.toString().replace("[", "")
                                                .replace("]", ""),
                                            style = TextStyle(fontSize = 16.sp)
                                        )
                                        Text(
                                            text = " (" + lecture.classroomids.toString()
                                                .replace("[", "")
                                                .replace("]", "") + " aud.)",
                                            style = TextStyle(
                                                color = ColorProvider(Color(0, 81, 255, 0xBB)),
                                                fontSize = 16.sp, fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }

                                    Text(
                                        text = lecture.uniperiod + " paskaita, " + lecture.starttime.toString() + "-" + lecture.endtime.toString() + " val.",
                                        style = TextStyle(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    fun openMainActivity(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = FLAG_ACTIVITY_NEW_TASK
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
        provideContent {
            Content(context)
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

        val api = Retrofit.Builder()
            .baseUrl(TimetableApi.BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

        val service = api.create(TimetableApi::class.java)


        // Data store id
        //val mainDataStorage = MainDataStorage(context)


        val id = MainDataStorage.getInstance(context).readString("TEACHER_ID")

        val request = service.getLectures(id.toString())


        val gson = Gson()
        val json = gson.toJson(request)

        updateAppWidgetState(context, glanceId) { prefs ->
            val currentCount = prefs[TimetableWidget.countKey]

            if (currentCount != null) {
                prefs[TimetableWidget.countKey] = json
            } else {
                prefs[TimetableWidget.countKey] = json
            }
        }
        TimetableWidget.update(context, glanceId)
    }
}