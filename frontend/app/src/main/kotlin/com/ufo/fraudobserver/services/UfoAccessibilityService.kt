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
        private const val CONTENT_DEBOUNCE_MS = 250L

        val ALLOWED_UPI_PACKAGES = setOf(
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app",                       // PhonePe
            "net.one97.paytm",                        // Paytm
            "in.org.npci.upiapp",                     // BHIM
            "com.dreamplug.androidapp",               // Cred
            "in.amazon.mShop.android.shopping"        // Amazon Pay
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var debounceJob: Job? = null
    private lateinit var riskEvaluator: RiskEvaluator

    // In-memory dedupe cache (vpa, amount, app)
    private var lastCheckedKey: String? = null
    private var lastCheckedTime: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        riskEvaluator = RiskEvaluator.getInstance(applicationContext)
        UfoProtectionPrefs.init(applicationContext)
        Log.d(TAG, "UfoAccessibilityService connected and monitoring UPI payment screens.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Hard constraint: If Prevention switch is OFF, ignore every event & make ZERO network calls
        if (!UfoProtectionPrefs.isBackgroundGuardEnabled(applicationContext)) {
            return
        }

        val pkg = event.packageName?.toString() ?: return
        // Allow supported UPI apps + own package (for demo payment simulation)
        val isAllowedPackage = ALLOWED_UPI_PACKAGES.contains(pkg) || pkg == applicationContext.packageName
        if (!isAllowedPackage) {
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

                val rootNode = rootInActiveWindow ?: return@launch
                val activePkg = packageName ?: rootNode.packageName?.toString() ?: return@launch

                val isAllowedPackage = ALLOWED_UPI_PACKAGES.contains(activePkg) || activePkg == applicationContext.packageName
                if (!isAllowedPackage) return@launch

                val startTime = System.currentTimeMillis()
                val payload = PayloadParser.parseAccessibilityTree(rootNode)

                if (payload != null && !payload.vpa.isNullOrBlank()) {
                    val vpa = payload.vpa
                    val amount = payload.amount ?: 0.0

                    // Debounce & dedupe: same (vpa, amount, app) in 5s window = ONE check
                    val dedupeKey = "$vpa:$amount:$activePkg"
                    val now = System.currentTimeMillis()
                    if (dedupeKey == lastCheckedKey && (now - lastCheckedTime < 5000L)) {
                        return@launch
                    }

                    lastCheckedKey = dedupeKey
                    lastCheckedTime = now

                    val parseElapsed = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Off-thread parsed payment page in ${parseElapsed}ms: $vpa, amount: $amount")

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
