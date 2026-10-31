package com.ufo.fraudobserver.data.remote

import com.google.gson.annotations.SerializedName

data class EvaluateRequest(
    @SerializedName("vpa") val vpa: String,
    @SerializedName("receiver") val receiver: String? = null,
    @SerializedName("payee_name") val payeeName: String? = null,
    @SerializedName("amount") val amount: Double = 0.0,
    @SerializedName("is_collect_request") val isCollectRequest: Boolean = false,
    @SerializedName("call_state_offhook") val callStateOffhook: Boolean = false,
    @SerializedName("source_app") val sourceApp: String? = null
)

data class EvaluateResponse(
    @SerializedName("risk") val risk: Float,
    @SerializedName("status") val status: String,
    @SerializedName("reasons") val reasons: List<String>,
    @SerializedName("vpa") val vpa: String?,
    @SerializedName("is_blacklisted") val isBlacklisted: Boolean,
    @SerializedName("action_required") val actionRequired: String
)

data class ReportRequest(
    @SerializedName("vpa") val vpa: String,
    @SerializedName("scam_category") val scamCategory: String,
    @SerializedName("source_app") val sourceApp: String?,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("notes") val notes: String? = null
)

data class ReportResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("vpa") val vpa: String,
    @SerializedName("recorded_at") val recordedAt: Long
)

data class BlacklistDeltaResponse(
    @SerializedName("since") val since: Long,
    @SerializedName("count") val count: Int,
    @SerializedName("blacklisted_vpas") val blacklistedVpas: List<String>,
    @SerializedName("timestamp") val timestamp: Long
)

