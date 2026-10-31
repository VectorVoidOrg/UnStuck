package com.ufo.fraudobserver.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface UfoApiService {

    @POST("/api/v1/evaluate")
    suspend fun evaluateTransaction(
        @Body request: EvaluateRequest
    ): Response<EvaluateResponse>

    @GET("/api/v1/blacklist/delta")
    suspend fun getBlacklistDelta(
        @Query("since") since: Long
    ): Response<BlacklistDeltaResponse>

    @POST("/api/v1/report")
    suspend fun reportScam(
        @Body report: ReportRequest
    ): Response<ReportResponse>

    companion object {
        // Standard Android emulator loopback to host or customizable via SharedPreferences
        private const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"

        fun create(baseUrl: String = DEFAULT_BASE_URL): UfoApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(UfoApiService::class.java)
        }
    }
}

