package com.ufo.fraudobserver.services

import android.app.Notification
import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.util.LruCache
import com.ufo.fraudobserver.engine.RiskEvaluator
import com.ufo.fraudobserver.parser.PayloadParser

/**
 * Module A.1: Notification Ingestion Service
 * Intercepts incoming UPI "Collect Request" notifications and payment alerts
 * before the user opens the payment app.
 */
class UfoNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "UfoNotificationService"
        private const val COOLDOWN_MS = 5000L

        // Target Package Filters from PRD Module A.1
        val TARGET_PACKAGES = setOf(
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app",                       // PhonePe
            "net.one97.paytm",                       // Paytm
            "in.org.npci.upiapp",                    // BHIM
            "in.amazon.mShop.android.shopping"       // Amazon Pay
        )
    }

    // Deduplication cache: hash(PackageName + Text) -> Timestamp
    private val deduplicationCache = LruCache<String, Long>(50)
    private lateinit var riskEvaluator: RiskEvaluator

    override fun onCreate() {
        super.onCreate()
        riskEvaluator = RiskEvaluator.getInstance(applicationContext)
        Log.d(TAG, "UfoNotificationService active.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName
        if (!TARGET_PACKAGES.contains(packageName)) {
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)

        val combinedContent = "${title ?: ""} ${text ?: ""} ${bigText ?: ""} ${subText ?: ""}".trim()
        if (combinedContent.isBlank()) return

        // Deduplication Check (PRD: 5000ms cooldown)
        val dedupeKey = "$packageName:$combinedContent"
        val lastSeen = deduplicationCache.get(dedupeKey) ?: 0L
        val now = System.currentTimeMillis()

        if (now - lastSeen < COOLDOWN_MS) {
            return
        }
        deduplicationCache.put(dedupeKey, now)

        // Parse notification payload using PayloadParser
        val parsed = PayloadParser.parseNotification(title, text ?: bigText)

        // If a VPA or Collect Request is detected, run risk evaluation
        if (!parsed.vpa.isNullOrBlank() || parsed.isCollectRequest) {
            val vpa = parsed.vpa ?: "collect_request@unknown"
            Log.i(TAG, "Ingested payment notification from $packageName: VPA=$vpa, isCollect=${parsed.isCollectRequest}")

            riskEvaluator.evaluateAndInterveneAsync(
                vpa = vpa,
                payeeName = parsed.payeeName,
                amount = parsed.amount,
                isCollectRequest = parsed.isCollectRequest,
                contextText = combinedContent,
                sourceApp = packageName
            )
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected! Re-binding component...")
        // Section 4 Mitigation: Overcome OEM background service killer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, UfoNotificationService::class.java))
        }
    }
}

