package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import retrofit2.http.GET

interface FirebaseApi {
    @GET("user-posts.json")
    suspend fun getChanges(): Map<String, ChangeDto>

    companion object {
        const val BASE_URL = "https://eif-courses.firebaseio.com/"
    }
}
