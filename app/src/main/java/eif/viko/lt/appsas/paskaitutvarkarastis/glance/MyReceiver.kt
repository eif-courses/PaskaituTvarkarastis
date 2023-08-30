package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.google.gson.Gson
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object TimetableWidget : GlanceAppWidget() {

    val countKey = stringPreferencesKey("count")

    @Composable
    fun Content() {
        val count = currentState(key = countKey) ?: "nera paskaitų"


        val gson = Gson()

        val lecturesDtos = gson.fromJson(count, Array<LecturesDto>::class.java)

        lecturesDtos.forEach {
            println(it.classroomids)
        }


        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(Color.Cyan),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            Text(
                text = "a",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(Color.Blue),
                    fontSize = 14.sp
                )
            )
            Button(
                text = "Inc",
                onClick = actionRunCallback(IncrementActionCallback::class.java)
            )
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Content()
        }
    }
}

class TimetableMyReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = TimetableWidget
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
        val request = service.getLectures("-1228")


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