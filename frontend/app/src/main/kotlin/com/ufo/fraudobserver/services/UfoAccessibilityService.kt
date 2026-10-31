package com.ufo.fraudobserver.services

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.ufo.fraudobserver.engine.RiskEvaluator
import com.ufo.fraudobserver.parser.PayloadParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Module A.2: Screen Scraping Engine
 * Extracts the receiver VPA, displayed banking name, and transaction amount
 * on the P2P amount-entry screen (immediately prior to the NPCI PIN pad).
 *
 * Implements all Section 4 engineering mitigations:
 * - BFS node traversal with isVisibleToUser filter
 * - 300ms debounce timer on TYPE_WINDOW_CONTENT_CHANGED
 * - 15-second memory cache to retain context during FLAG_SECURE PIN pad blinding
 * - Dedicated CoroutineScope(Dispatchers.Default) to prevent UI thread freezing
 */
class UfoAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UfoAccessibilityService"
        private const val CONTENT_DEBOUNCE_MS = 300L
        private const val FLAG_SECURE_CACHE_TTL_MS = 15_000L // 15 seconds context retention
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
        Log.d(TAG, "UfoAccessibilityService successfully connected and monitoring UPI screens.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Immediate extraction when screen transitions
                processActiveWindow(event.packageName?.toString())
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 300ms debounce to prevent CPU starvation / UI stuttering while user types
                debounceJob?.cancel()
                debounceJob = serviceScope.launch {
                    delay(CONTENT_DEBOUNCE_MS)
                    processActiveWindow(event.packageName?.toString())
                }
            }
        }
    }

    private fun processActiveWindow(packageName: String?) {
        serviceScope.launch {
            try {
                val rootNode = rootInActiveWindow
                if (rootNode == null) {
                    // Possible FLAG_SECURE screen blinding (e.g. NPCI PIN pad)!
                    val now = System.currentTimeMillis()
                    if (lastSeenVpa != null && (now - lastSeenTimestamp < FLAG_SECURE_CACHE_TTL_MS)) {
                        Log.d(TAG, "FLAG_SECURE active. Context retained from 15-second cache: VPA=$lastSeenVpa")
                    }
                    return@launch
                }

                // Parse tree via BFS ignoring hidden views
                val payload = PayloadParser.parseAccessibilityTree(rootNode)

                if (payload != null && !payload.vpa.isNullOrBlank()) {
                    val vpa = payload.vpa

                    // Cache in memory for 15 seconds
                    lastSeenVpa = vpa
                    lastSeenPayeeName = payload.payeeName
                    lastSeenAmount = payload.amount
                    lastSeenTimestamp = System.currentTimeMillis()

                    // Evaluate risk and intervene if anomalous
                    riskEvaluator.evaluateAndInterveneAsync(
                        vpa = vpa,
                        payeeName = payload.payeeName,
                        amount = payload.amount,
                        isCollectRequest = payload.isCollectRequest,
                        contextText = payload.rawTextDump,
                        sourceApp = packageName
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

