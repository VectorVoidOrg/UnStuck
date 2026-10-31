package com.ufo.fraudobserver.engine

import java.util.Locale
import kotlin.math.min

/**
 * Layer 2: Heuristic & Behavioral Analysis Engine
 * Evaluates zero-day scammer accounts not yet in the blacklist and calculates
 * a cumulative RiskScore (0 to 100).
 */
class HeuristicEngine {

    companion object {
        // High-profile static dictionary for brand spoofing detection
        val HIGH_PROFILE_BRANDS = listOf(
            "pmcares", "irctc", "bescom", "sbi", "hdfc", "icici",
            "paytm", "phonepe", "gpay", "customercare", "refund", "kyc"
        )

        // Deceptive Handle Keywords
        val DECEPTIVE_KEYWORDS = listOf(
            "refund", "cashback", "prize", "lottery", "verify",
            "block", "kyc", "support", "helpline", "reward", "winner"
        )

        // Collect-to-Receive Trap Lure Keywords
        val RECEIVE_LURE_KEYWORDS = listOf(
            "receive", "received", "credited", "accept", "credit", "claim", "won"
        )
    }

    data class HeuristicResult(
        val riskScoreDelta: Int,
        val triggers: List<String>
    )

    /**
     * Evaluates VPA, Payee Name, Collect Request flag, and Context text.
     */
    fun evaluate(
        vpa: String,
        payeeName: String?,
        isCollectRequest: Boolean,
        contextText: String?
    ): HeuristicResult {
        var scoreDelta = 0
        val triggers = mutableListOf<String>()

        val normalizedVpa = vpa.trim().lowercase(Locale.ROOT)
        val handlePrefix = if (normalizedVpa.contains("@")) {
            normalizedVpa.substringBefore("@")
        } else {
            normalizedVpa
        }
        val cleanHandle = handlePrefix.replace(".", "").replace("-", "").replace("_", "")

        val nameNormalized = payeeName?.lowercase(Locale.ROOT) ?: ""

        // 1. Brand Spoofing / Typosquatting Check (Levenshtein Edit Distance 1 or 2)
        for (brand in HIGH_PROFILE_BRANDS) {
            val dist = levenshteinDistance(cleanHandle, brand)
            if (dist in 1..2 || (cleanHandle.contains(brand) && cleanHandle != brand)) {
                scoreDelta += 60
                triggers.add("Brand Spoofing: Mimics '$brand' (edit distance $dist)")
                break
            }
        }

        // 2. Deceptive Handle Keywords Check (+50)
        for (kw in DECEPTIVE_KEYWORDS) {
            if (handlePrefix.contains(kw) || nameNormalized.contains(kw)) {
                scoreDelta += 50
                triggers.add("Deceptive Keyword Detected: Handle or Payee contains '$kw'")
                break
            }
        }

        // 3. Collect-to-Receive Trap Detection (+75)
        if (isCollectRequest) {
            val combinedText = "$contextText $payeeName".lowercase(Locale.ROOT)
            val hasReceiveLure = RECEIVE_LURE_KEYWORDS.any { combinedText.contains(it) }
            if (hasReceiveLure) {
                scoreDelta += 75
                triggers.add("Collect-to-Receive Trap: Disguised as incoming money credit")
            } else {
                scoreDelta += 30
                triggers.add("Unsolicited Collect Request")
            }
        }

        return HeuristicResult(scoreDelta, triggers)
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[s1.length][s2.length]
    }
}

