package eif.viko.lt.appsas.paskaitutvarkarastis.glance

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The one place HTTP clients are built. Everything is lazy, so nothing is constructed until
 * something actually makes a call, and the two Retrofit instances share a single
 * [OkHttpClient] rather than each spinning up its own connection and thread pools.
 */
object HttpClients {

    /** Shared by both Retrofit instances. */
    val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val timetableRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(TimetableApi.BASE_URL)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }

    // Gson, not Moshi: Firebase serves `paskaita` as either a string or a number, and Moshi
    // throws on the numeric case. The converter split is the reason these stay separate.
    private val firebaseRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(FirebaseApi.BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val timetableApi: TimetableApi by lazy {
        timetableRetrofit.create(TimetableApi::class.java)
    }

    val firebaseApi: FirebaseApi by lazy {
        firebaseRetrofit.create(FirebaseApi::class.java)
    }
}
