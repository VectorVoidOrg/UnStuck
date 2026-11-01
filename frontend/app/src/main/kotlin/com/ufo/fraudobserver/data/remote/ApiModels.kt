package com.ufo.fraudobserver.data.remote

import com.google.gson.annotations.SerializedName

// ==========================================
// Gateway Prevention Schemas
// ==========================================

data class PreventionCheckRequest(
    @SerializedName("check_id") val checkId: String,
    @SerializedName("receiver_vpa") val receiverVpa: String,
    @SerializedName("receiver_name") val receiverName: String? = null,
    @SerializedName("amount") val amount: Double = 0.0,
    @SerializedName("source_app") val sourceApp: String? = null,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)

data class PreventionCheckResponse(
    @SerializedName("check_id") val checkId: String,
    @SerializedName("risk_score") val riskScore: Int,
    @SerializedName("verdict") val verdict: String,
    @SerializedName("reasons") val reasons: List<String> = emptyList(),
    @SerializedName("remaining_free") val remainingFree: Int = 100,
    @SerializedName("is_partial_check") val isPartialCheck: Boolean = false
)

// ==========================================
// Gateway Auth Schemas
// ==========================================

data class NonceResponse(
    @SerializedName("nonce") val nonce: String,
    @SerializedName("expires_in_seconds") val expiresInSeconds: Int = 300
)

data class GoogleAuthRequest(
    @SerializedName("id_token") val idToken: String,
    @SerializedName("nonce") val nonce: String
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String = "bearer",
    @SerializedName("expires_in") val expiresIn: Int = 900,
    @SerializedName("user_id") val userId: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("is_demo") val isDemo: Boolean = false
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class UserProfileResponse(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("email_verified") val emailVerified: Boolean = false,
    @SerializedName("is_demo") val isDemo: Boolean = false,
    @SerializedName("plan") val plan: String = "FREE",
    @SerializedName("is_entitled") val isEntitled: Boolean = false,
    @SerializedName("prevention_used") val preventionUsed: Int = 0,
    @SerializedName("prevention_remaining") val preventionRemaining: Int = 100,
    @SerializedName("unstuck_used") val unstuckUsed: Int = 0,
    @SerializedName("unstuck_remaining") val unstuckRemaining: Int = 5,
    @SerializedName("entitlement_expires_at") val entitlementExpiresAt: String? = null
)

// ==========================================
// Gateway Billing Schemas
// ==========================================

data class BillingAccountIdResponse(
    @SerializedName("account_id_hash") val accountIdHash: String
)

data class BillingVerifyRequest(
    @SerializedName("purchase_token") val purchaseToken: String,
    @SerializedName("product_id") val productId: String
)

data class BillingVerifyResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("state") val state: String,
    @SerializedName("is_entitled") val isEntitled: Boolean,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("message") val message: String = ""
)

// ==========================================
// Gateway Demo Schemas
// ==========================================

data class DemoLoginRequest(
    @SerializedName("access_code") val accessCode: String
)

data class DemoSetUsageRequest(
    @SerializedName("prevention_used") val preventionUsed: Int? = null,
    @SerializedName("unstuck_used") val unstuckUsed: Int? = null
)

data class DemoScenarioItem(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("receiver_vpa") val receiverVpa: String,
    @SerializedName("receiver_name") val receiverName: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("expected_verdict") val expectedVerdict: String,
    @SerializedName("expected_score_range") val expectedScoreRange: List<Int> = emptyList()
)

// ==========================================
// Legacy Compatibility Schemas
// ==========================================

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

