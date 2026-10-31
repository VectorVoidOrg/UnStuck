package com.ufo.fraudobserver.services

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.ufo.fraudobserver.engine.RiskEvaluator
import com.ufo.fraudobserver.engine.UfoProtectionPrefs
import com.ufo.fraudobserver.parser.PayloadParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Module A.2: Real-Time UPI Screen Scraping Engine
 * Monitors active UPI apps in the background. As soon as the user reaches the screen
 * showing the receiver's details (name/VPA) and amount, it extracts the data in real time,
 * sends it to the backend via RiskEvaluator, and displays the Truecaller-style risk popup.
 */
class UfoAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UfoAccessibilityService"
        private const val CONTENT_DEBOUNCE_MS = 280L
        private const val FLAG_SECURE_CACHE_TTL_MS = 15_000L

        private val IGNORED_PACKAGES = setOf(
            "com.ufo.fraudobserver",
            "com.android.systemui",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.android.settings"
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var debounceJob: Job? = null
    private lateinit var riskEvaluator: RiskEvaluator

    // In-memory cache for FLAG_SECURE screen blinding mitigation
    private var lastSeenVpa: String? = null
    private var lastSeenPayeeName: String? = null
    private var lastSeenAmount: Double? = null
    private var lastSeenTimestamp: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        riskEvaluator = RiskEvaluator.getInstance(applicationContext)
        UfoProtectionPrefs.init(applicationContext)
        Log.d(TAG, "UfoAccessibilityService connected and monitoring UPI payment screens.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Respect user's choice: only extract when Background UPI Guard is enabled
        if (!UfoProtectionPrefs.isBackgroundGuardEnabled(applicationContext)) {
            return
        }

        val pkg = event.packageName?.toString()
        if (pkg != null && IGNORED_PACKAGES.contains(pkg)) {
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                processActiveWindow(pkg)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                debounceJob?.cancel()
                debounceJob = serviceScope.launch {
                    delay(CONTENT_DEBOUNCE_MS)
                    processActiveWindow(pkg)
                }
            }
        }
    }

    private fun processActiveWindow(packageName: String?) {
        serviceScope.launch {
            try {
                if (!UfoProtectionPrefs.isBackgroundGuardEnabled(applicationContext)) return@launch

                val rootNode = rootInActiveWindow
                if (rootNode == null) {
                    val now = System.currentTimeMillis()
                    if (lastSeenVpa != null && (now - lastSeenTimestamp < FLAG_SECURE_CACHE_TTL_MS)) {
                        Log.d(TAG, "FLAG_SECURE active. Context retained from 15-second cache: VPA=$lastSeenVpa")
                    }
                    return@launch
                }

                val activePkg = packageName ?: rootNode.packageName?.toString()
                if (activePkg != null && IGNORED_PACKAGES.contains(activePkg)) {
                    return@launch
                }

                val payload = PayloadParser.parseAccessibilityTree(rootNode)

                if (payload != null && !payload.vpa.isNullOrBlank()) {
                    val vpa = payload.vpa

                    lastSeenVpa = vpa
                    lastSeenPayeeName = payload.payeeName
                    lastSeenAmount = payload.amount
                    lastSeenTimestamp = System.currentTimeMillis()

                    riskEvaluator.evaluateAndInterveneAsync(
                        vpa = vpa,
                        payeeName = payload.payeeName,
                        amount = payload.amount,
                        isCollectRequest = payload.isCollectRequest,
                        contextText = payload.rawTextDump,
                        sourceApp = activePkg
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during accessibility BFS screen processing", e)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "UfoAccessibilityService interrupted.")
        debounceJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        debounceJob?.cancel()
    }
}
