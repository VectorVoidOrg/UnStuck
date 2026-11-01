package com.ufo.fraudobserver.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface UfoApiService {

    // ==========================================
    // Gateway Prevention Endpoint
    // ==========================================
    @POST("/prevention/check")
    suspend fun checkPrevention(
        @Body request: PreventionCheckRequest
    ): Response<PreventionCheckResponse>

    // ==========================================
    // Gateway Auth Endpoints
    // ==========================================
    @GET("/auth/nonce")
    suspend fun getNonce(): Response<NonceResponse>

    @POST("/auth/google")
    suspend fun authGoogle(
        @Body request: GoogleAuthRequest
    ): Response<TokenResponse>

    @POST("/auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshRequest
    ): Response<TokenResponse>

    @POST("/auth/logout")
    suspend fun logout(
        @Body request: RefreshRequest
    ): Response<Map<String, String>>

    @GET("/me")
    suspend fun getProfile(): Response<UserProfileResponse>

    @DELETE("/account")
    suspend fun deleteAccount(): Response<Map<String, String>>

    // ==========================================
    // Gateway Billing Endpoints
    // ==========================================
    @GET("/billing/account-id")
    suspend fun getBillingAccountId(): Response<BillingAccountIdResponse>

    @POST("/billing/verify")
    suspend fun verifySubscription(
        @Body request: BillingVerifyRequest
    ): Response<BillingVerifyResponse>

    // ==========================================
    // Gateway Demo Endpoints
    // ==========================================
    @POST("/demo/login")
    suspend fun demoLogin(
        @Body request: DemoLoginRequest
    ): Response<TokenResponse>

    @POST("/demo/reset")
    suspend fun demoReset(): Response<Map<String, String>>

    @POST("/demo/set-usage")
    suspend fun demoSetUsage(
        @Body request: DemoSetUsageRequest
    ): Response<Map<String, Int>>

    @POST("/demo/simulate-purchase")
    suspend fun demoSimulatePurchase(): Response<Map<String, Any>>

    @POST("/demo/simulate-cancel")
    suspend fun demoSimulateCancel(): Response<Map<String, Any>>

    @GET("/demo/scenarios")
    suspend fun getDemoScenarios(): Response<List<DemoScenarioItem>>

    // ==========================================
    // Legacy Compatibility Endpoints
    // ==========================================
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
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"

        fun create(context: android.content.Context): UfoApiService {
            val store = com.ufo.fraudobserver.data.local.SecureTokenStore.getInstance(context)
            val baseUrl = com.ufo.fraudobserver.BuildConfig.GATEWAY_BASE_URL
            val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            return create(baseUrl = url, tokenProvider = { store.getAccessTokenSync() })
        }

        fun create(
            baseUrl: String = DEFAULT_BASE_URL,
            tokenProvider: (() -> String?)? = null
        ): UfoApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val headerInterceptor = Interceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                    .header("ngrok-skip-browser-warning", "true")
                    .header("User-Agent", "UFO-Android-Client")

                val token = tokenProvider?.invoke()
                if (!token.isNullOrBlank()) {
                    builder.header("Authorization", "Bearer $token")
                }

                chain.proceed(builder.build())
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(1800, TimeUnit.MILLISECONDS)
                .readTimeout(1800, TimeUnit.MILLISECONDS)
                .addInterceptor(headerInterceptor)
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

