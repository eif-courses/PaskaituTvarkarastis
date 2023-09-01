package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import retrofit2.http.GET
import retrofit2.http.Query

interface TimetableApi {
    @GET("timetable/teacher")
    suspend fun getLectures(@Query("teacher_id") id: String): List<LecturesDto>

    @GET("timetable/teachers/ids")
    suspend fun getTeachersIds(): List<TeacherDto>


    companion object{
        const val BASE_URL = "https://gemshop-production.up.railway.app/"
    }
}