package eif.viko.lt.appsas.paskaitutvarkarastis

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eif.viko.lt.appsas.paskaitutvarkarastis.glance.TeacherDto
import eif.viko.lt.appsas.paskaitutvarkarastis.glance.TimetableApi
import eif.viko.lt.appsas.paskaitutvarkarastis.glance.TimetableWidget
import eif.viko.lt.appsas.paskaitutvarkarastis.ui.theme.PaskaituTvarkarastisTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class MainActivity : ComponentActivity() {
    val teachers = mutableStateOf<List<TeacherDto>>(emptyList())

    private val mainDataStorage = MainDataStorage.getInstance(this)
    val coroutineScope = CoroutineScope(Dispatchers.IO)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaskaituTvarkarastisTheme {
                // Call the service.getTeachersIds() function in the LaunchedEffect function
                val api = Retrofit.Builder()
                    .baseUrl(TimetableApi.BASE_URL)
                    .addConverterFactory(MoshiConverterFactory.create())
                    .build()
                val service = api.create(TimetableApi::class.java)

                LaunchedEffect(key1 = "aa") {
                    val response = service.getTeachersIds()

                    // Store the result of the service.getTeachersIds() function in the teachers variable
                    teachers.value = response
                }
                ConfigurationActivity()
            }
        }
    }


    @Composable
    fun CustomListItem(teacherDto: TeacherDto, onListItemClicked: () -> Unit) {
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
                Text(teacherDto.short)
            }
        }
    }

    @Composable
    fun ConfigurationActivity() {

        //var text by remember { mutableStateOf("") }
        val teacherState = teachers

        // Check if the state of the teachers variable is not empty
        if (teacherState.value.isNotEmpty()) {
            // Call the itemsIndexed() modifier
            LazyColumn {
                itemsIndexed(teacherState.value) { _, teacher ->
                    // Display each teacher in the list

                    CustomListItem(teacherDto = teacher) {
                        coroutineScope.launch {
                            mainDataStorage.writeString("TEACHER_ID", teacher.id)
                        }
                        Toast.makeText(this@MainActivity, "Paskauskite atnaujinti, kad matytumėte tvarkaraščio pakeitimus!", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        } else {
            Text("Programėlė neveikia arba nėra interneto :)")
        }
    }

}
