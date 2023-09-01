package eif.viko.lt.appsas.paskaitutvarkarastis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eif.viko.lt.appsas.paskaitutvarkarastis.glance.TeacherDto
import eif.viko.lt.appsas.paskaitutvarkarastis.glance.TimetableApi
import eif.viko.lt.appsas.paskaitutvarkarastis.ui.theme.PaskaituTvarkarastisTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    fun ConfigurationActivity() {


        var text by remember { mutableStateOf("") }
        val teach = teachers

        // Check if the state of the teachers variable is not empty
        if (teach.value.isNotEmpty()) {
            // Call the itemsIndexed() modifier
            LazyColumn {
                itemsIndexed(teach.value) { _, teacher ->
                    // Display each teacher in the list
                    Text(text = teacher.id)
                }
            }
        } else {
            // Do not call the itemsIndexed() modifier
        }

//        Column {
//
//            Text("Enter your name:")
//            TextField(value = text, onValueChange = { text = it })
//            Button(onClick = {
//
//                coroutineScope.launch {
//                    mainDataStorage.writeString("TEACHER_ID", text)
//                }
//
//                // Finish the activity
//                setResult(RESULT_OK)
//                finish()
//            }) {
//                Text("Save")
//            }
//        }
    }

}
