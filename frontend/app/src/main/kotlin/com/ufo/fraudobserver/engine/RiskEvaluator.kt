package com.ufo.fraudobserver.engine

import android.content.Context
import android.util.Log
import com.ufo.fraudobserver.data.remote.EvaluateRequest
import com.ufo.fraudobserver.data.remote.UfoApiService
import com.ufo.fraudobserver.data.repository.FraudRepository
import com.ufo.fraudobserver.intervention.InterventionNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.math.roundToInt

data class RiskEvaluationResult(
    val vpa: String,
    val payeeName: String?,
    val amount: Double?,
    val riskScore: Int,
    val status: RiskStatus,
    val triggers: List<String>,
    val isHardBlocked: Boolean,
    val latencyMs: Long,
    val backendVerified: Boolean = false,
    val sourceApp: String? = null
)

enum class RiskStatus {
    SAFE,
    WARNING,
    FRAUD
}

/**
 * Module B: Real-Time Backend + On-Device Risk Evaluation Engine
 * 1. Sends extracted UPI screen payload (Receiver, VPA, Amount, Source App) to Backend API (/api/v1/evaluate).
 * 2. Uses Backend Risk Score when reachable; seamlessly falls back to on-device multi-layer engine while backend is under development.
 * 3. Triggers the Truecaller-sized notification popup with the resulting risk score.
 */
class RiskEvaluator(
    private val context: Context,
    private val bloomFilter: BloomFilterEngine = BloomFilterEngine.getInstance(context),
    private val heuristicEngine: HeuristicEngine = HeuristicEngine(),
    private val callStateMonitor: CallStateMonitor = CallStateMonitor.getInstance(context),
    private val repository: FraudRepository = FraudRepository(context),
    private val apiService: UfoApiService = UfoApiService.create(),
    private val notifier: InterventionNotifier = InterventionNotifier.getInstance(context)
) {

    companion object {
        private const val TAG = "RiskEvaluator"
        private const val VPA_ALERT_COOLDOWN_MS = 5000L // 5 seconds suppression window

        @Volatile
        private var INSTANCE: RiskEvaluator? = null

        fun getInstance(context: Context): RiskEvaluator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RiskEvaluator(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val recentAlertCooldownMap = ConcurrentHashMap<String, Long>()

    /**
     * Evaluates risk asynchronously when a UPI app screen or notification is intercepted in background.
     */
    fun evaluateAndInterveneAsync(
        vpa: String,
        payeeName: String?,
        amount: Double?,
        isCollectRequest: Boolean,
        contextText: String?,
        sourceApp: String?
    ) {
        // Respect user's choice: if Background UPI Guard is turned off, do not run background interception
        if (!UfoProtectionPrefs.isBackgroundGuardEnabled(context)) {
            Log.d(TAG, "Background UPI Guard is disabled by user choice; skipping screen evaluation.")
            return
        }

        scope.launch {
            evaluate(
                vpa = vpa,
                payeeName = payeeName,
                amount = amount,
                isCollectRequest = isCollectRequest,
                contextText = contextText,
                sourceApp = sourceApp,
                triggerIntervention = true
            )
        }
    }

    suspend fun evaluate(
        vpa: String,
        payeeName: String?,
        amount: Double?,
        isCollectRequest: Boolean,
        contextText: String?,
        sourceApp: String?,
        triggerIntervention: Boolean = true
    ): RiskEvaluationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val normalizedVpa = vpa.trim().lowercase()

        // Check Session Whitelist (User dismissed within 5 minutes)
        if (SessionWhitelist.isWhitelisted(normalizedVpa)) {
            val elapsed = System.currentTimeMillis() - startTime
            return@withContext RiskEvaluationResult(
                vpa = vpa,
                payeeName = payeeName,
                amount = amount,
                riskScore = 0,
                status = RiskStatus.SAFE,
                triggers = listOf("Session Whitelisted by User"),
                isHardBlocked = false,
                latencyMs = elapsed,
                sourceApp = sourceApp
            )
        }

        // Check Permanent Whitelist from Room
        if (repository.isWhitelisted(normalizedVpa)) {
            val elapsed = System.currentTimeMillis() - startTime
            return@withContext RiskEvaluationResult(
                vpa = vpa,
                payeeName = payeeName,
                amount = amount,
                riskScore = 0,
                status = RiskStatus.SAFE,
                triggers = listOf("Trusted Receiver Whitelist"),
                isHardBlocked = false,
                latencyMs = elapsed,
                sourceApp = sourceApp
            )
        }

        val callActive = callStateMonitor.isCallActive()
        val triggers = mutableListOf<String>()
        var finalScore = 0
        var isHardBlocked = false
        var backendVerified = false

        // Step 1: Send extracted screen details (Receiver, VPA, Amount) to Backend (/api/v1/evaluate)
        try {
            val response = apiService.evaluateTransaction(
                EvaluateRequest(
                    vpa = normalizedVpa,
                    receiver = payeeName,
                    payeeName = payeeName,
                    amount = amount ?: 0.0,
                    isCollectRequest = isCollectRequest,
                    callStateOffhook = callActive,
                    sourceApp = sourceApp
                )
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                backendVerified = true
                finalScore = if (body.risk <= 1.0f && body.risk > 0f) {
                    (body.risk * 100f).roundToInt()
                } else {
                    body.risk.roundToInt().coerceIn(0, 100)
                }
                isHardBlocked = body.isBlacklisted || finalScore >= 90
                triggers.addAll(body.reasons)
                Log.d(TAG, "Backend evaluation succeeded: score=$finalScore, reasons=${body.reasons}")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Backend under process / unreachable (${e.message}), using on-device risk engine fallback.")
        }

        // Step 2: If backend is under process / offline, compute via on-device risk engine
        if (!backendVerified) {
            var cumulativeScore = 0

            // Layer 1: Zero-Latency Local Bloom Filter
            if (bloomFilter.contains(normalizedVpa)) {
                cumulativeScore = 99
                isHardBlocked = true
                triggers.add("Blacklisted Fraud ID: Matched in fraud signature database")
            }

            // Layer 2: Heuristic & Behavioral Analysis
            if (!isHardBlocked) {
                val heuristicResult = heuristicEngine.evaluate(
                    vpa = vpa,
                    payeeName = payeeName,
                    isCollectRequest = isCollectRequest,
                    contextText = contextText
                )
                cumulativeScore += heuristicResult.riskScoreDelta
                triggers.addAll(heuristicResult.triggers)
            }

            // Layer 3: Live Call / Vishing Telemetry
            if (callActive) {
                cumulativeScore += 50
                triggers.add("ACTIVE CALL DETECTED: Potential vishing pressure")
            }

            finalScore = min(cumulativeScore, 100)
        }

        if (triggers.isEmpty()) {
            triggers.add("Verified Receiver • No fraud indicators matched")
        }

        val status = when {
            finalScore >= 60 -> RiskStatus.FRAUD
            finalScore >= 30 -> RiskStatus.WARNING
            else -> RiskStatus.SAFE
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Risk evaluation finished in ${elapsed}ms: score=$finalScore, status=$status, vpa=$vpa")

        val result = RiskEvaluationResult(
            vpa = vpa,
            payeeName = payeeName,
            amount = amount,
            riskScore = finalScore,
            status = status,
            triggers = triggers,
            isHardBlocked = isHardBlocked,
            latencyMs = elapsed,
            backendVerified = backendVerified,
            sourceApp = sourceApp
        )

        // Step 3: Show Truecaller-Style Notification Popup & Record Check
        if (triggerIntervention) {
            val shouldShowForCurrentStatus = status != RiskStatus.SAFE || UfoProtectionPrefs.shouldShowPopupForSafe(context)
            val dedupeKey = "$normalizedVpa:${amount ?: 0.0}"
            val lastAlertTime = recentAlertCooldownMap[dedupeKey] ?: 0L
            val now = System.currentTimeMillis()

            if (now - lastAlertTime > VPA_ALERT_COOLDOWN_MS) {
                recentAlertCooldownMap[dedupeKey] = now

                repository.recordFlaggedTransaction(
                    vpa = vpa,
                    payeeName = payeeName,
                    amount = amount,
                    riskScore = finalScore,
                    triggerReason = triggers.joinToString(" • "),
                    sourceApp = sourceApp
                )

                if (shouldShowForCurrentStatus) {
                    notifier.showIntervention(
                        vpa = vpa,
                        riskScore = finalScore,
                        primaryTrigger = triggers.firstOrNull() ?: "Evaluated by UFO Guard",
                        secondaryTrigger = triggers.drop(1).firstOrNull(),
                        payeeName = payeeName,
                        amount = amount,
                        sourceApp = sourceApp,
                        backendVerified = backendVerified
                    )
                }
            }
        }

        return@withContext result
    }
}
