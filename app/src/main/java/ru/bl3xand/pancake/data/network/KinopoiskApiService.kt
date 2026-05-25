package ru.bl3xand.pancake.data.network

import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import ru.bl3xand.pancake.BuildConfig
import ru.bl3xand.pancake.data.model.network.KinopoiskResponse

interface KinopoiskApiService {
    @GET("v1.3/movie")
    fun searchMovies(
        @Query("name") query: String,
        @Query("limit") limit: Int = 10
    ): Call<KinopoiskResponse>

    companion object {
        fun create(): KinopoiskApiService {
            val apiKey = BuildConfig.KINOPOISK_API_KEY.trim()
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val requestBuilder = chain.request().newBuilder()
                    if (apiKey.isNotBlank()) {
                        requestBuilder.addHeader("X-API-KEY", apiKey)
                    }
                    chain.proceed(requestBuilder.build())
                }
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.kinopoisk.dev/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(KinopoiskApiService::class.java)
        }
    }
}