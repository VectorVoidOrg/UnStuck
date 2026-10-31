package com.ufo.fraudobserver.engine

import android.content.Context
import android.util.Log
import com.ufo.fraudobserver.data.repository.FraudRepository
import com.ufo.fraudobserver.intervention.InterventionNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

data class RiskEvaluationResult(
    val vpa: String,
    val payeeName: String?,
    val amount: Double?,
    val riskScore: Int,
    val status: RiskStatus,
    val triggers: List<String>,
    val isHardBlocked: Boolean,
    val latencyMs: Long
)

enum class RiskStatus {
    SAFE,
    WARNING,
    FRAUD
}

/**
 * Module B: Multi-Layered Risk Evaluation Engine
 * Executes with sub-50ms latency on Dispatchers.Default.
 */
class RiskEvaluator(
    private val context: Context,
    private val bloomFilter: BloomFilterEngine = BloomFilterEngine.getInstance(context),
    private val heuristicEngine: HeuristicEngine = HeuristicEngine(),
    private val callStateMonitor: CallStateMonitor = CallStateMonitor.getInstance(context),
    private val repository: FraudRepository = FraudRepository(context),
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
     * Evaluates risk asynchronously on Dispatchers.Default without blocking caller.
     */
    fun evaluateAndInterveneAsync(
        vpa: String,
        payeeName: String?,
        amount: Double?,
        isCollectRequest: Boolean,
        contextText: String?,
        sourceApp: String?
    ) {
        scope.launch {
            evaluate(vpa, payeeName, amount, isCollectRequest, contextText, sourceApp, triggerIntervention = true)
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
    ): RiskEvaluationResult = withContext(Dispatchers.Default) {
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
                latencyMs = elapsed
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
                latencyMs = elapsed
            )
        }

        val triggers = mutableListOf<String>()
        var cumulativeScore = 0
        var isHardBlocked = false

        // Layer 1: Zero-Latency Local Bloom Filter
        if (bloomFilter.contains(normalizedVpa)) {
            cumulativeScore = 99
            isHardBlocked = true
            triggers.add("Blacklisted Fraud ID: Matched in offline fraud database")
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
        val callActive = callStateMonitor.isCallActive()
        if (callActive) {
            cumulativeScore += 50
            triggers.add("ACTIVE CALL DETECTED: Scammers stay on calls to pressure victims")
        }

        val finalScore = min(cumulativeScore, 100)
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
            latencyMs = elapsed
        )

        // Trigger Intervention if risky and cooldown elapsed
        if (triggerIntervention && status != RiskStatus.SAFE) {
            val lastAlertTime = recentAlertCooldownMap[normalizedVpa] ?: 0L
            val now = System.currentTimeMillis()

            if (now - lastAlertTime > VPA_ALERT_COOLDOWN_MS) {
                recentAlertCooldownMap[normalizedVpa] = now

                // Save to Room SQLite database
                repository.recordFlaggedTransaction(
                    vpa = vpa,
                    payeeName = payeeName,
                    amount = amount,
                    riskScore = finalScore,
                    triggerReason = triggers.joinToString(" • "),
                    sourceApp = sourceApp
                )

                // Dispatch non-blocking heads-up notification
                notifier.showIntervention(
                    vpa = vpa,
                    riskScore = finalScore,
                    primaryTrigger = triggers.firstOrNull() ?: "High Risk Anomaly",
                    secondaryTrigger = triggers.drop(1).joinToString(" • "),
                    payeeName = payeeName,
                    amount = amount
                )
            }
        }

        return@withContext result
    }
}

