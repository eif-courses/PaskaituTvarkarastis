package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * `from`/`to` are inclusive yyyy-MM-dd bounds. Both are optional and Retrofit omits a null
 * query param, so leaving them out keeps the pre-range behaviour of returning the current week.
 */
interface TimetableApi {
    @GET("timetable/teacher")
    suspend fun getLectures(
        @Query("teacher_id") id: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): List<LecturesDto>

    @GET("timetable/group")
    suspend fun getGroupLectures(
        @Query("group_id") id: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): List<LecturesDto>

    @GET("timetable/classroom")
    suspend fun getClassroomLectures(
        @Query("classroom_id") id: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): List<LecturesDto>

    @GET("timetable/teachers/ids")
    suspend fun getTeachersIds(): List<EntityDto>

    @GET("timetable/groups/ids")
    suspend fun getGroupIds(): List<EntityDto>

    @GET("timetable/classrooms/ids")
    suspend fun getClassroomIds(): List<EntityDto>

    @GET("timetable/currentweek")
    suspend fun getCurrentWeek(): Int



    companion object{
        const val BASE_URL = "https://onlinecourses-production.up.railway.app/"
    }
}
