package com.ufo.fraudobserver.intervention

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.ufo.fraudobserver.R
import com.ufo.fraudobserver.UfoMainActivity
import java.text.NumberFormat
import java.util.Locale

/**
 * Module C: Truecaller-Style Non-Blocking Notification Popup
 * Renders a compact, professional floating heads-up card (matching Truecaller's message popup size)
 * showing the extracted Receiver, VPA, Amount, and Risk Score from the backend/risk engine.
 */
class InterventionNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "ufo_truecaller_popup_v2"
        private const val NOTIFICATION_ID_BASE = 8080

        @Volatile
        private var INSTANCE: InterventionNotifier? = null

        fun getInstance(context: Context): InterventionNotifier {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InterventionNotifier(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()

            val alertSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val channel = NotificationChannel(
                CHANNEL_ID,
                "UFO Live UPI Risk Popups",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Truecaller-style instant risk score popup when a UPI payment screen is detected"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 120, 250)
                setSound(alertSound, audioAttributes)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(true)
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showIntervention(
        vpa: String,
        riskScore: Int,
        primaryTrigger: String,
        secondaryTrigger: String?,
        payeeName: String?,
        amount: Double?,
        sourceApp: String? = null,
        backendVerified: Boolean = false
    ) {
        val notificationId = NOTIFICATION_ID_BASE + (vpa.hashCode() and 0xFFFF)

        val abortIntent = Intent(context, UfoMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "ALERT_DETAIL")
            putExtra("EXTRA_VPA", vpa)
            putExtra("EXTRA_PAYEE", payeeName)
            putExtra("EXTRA_AMOUNT", amount ?: 0.0)
            putExtra("EXTRA_RISK_SCORE", riskScore)
            putExtra("EXTRA_TRIGGER", primaryTrigger)
            putExtra("NOTIFICATION_ID", notificationId)
        }
        val abortPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            abortIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, DismissReceiver::class.java).apply {
            putExtra("EXTRA_VPA", vpa)
            putExtra("NOTIFICATION_ID", notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appLabel = formatSourceAppName(sourceApp).uppercase(Locale.getDefault())
        val engineTag = if (backendVerified) "CLOUD SCORE" else "LIVE SCORE"

        val badgeRes = when {
            riskScore >= 60 -> R.drawable.bg_risk_badge
            riskScore >= 30 -> R.drawable.bg_warn_badge
            else -> R.drawable.bg_safe_badge
        }
        val badgeText = when {
            riskScore >= 60 -> "HIGH RISK · $riskScore/100"
            riskScore >= 30 -> "CAUTION · $riskScore/100"
            else -> "SAFE · $riskScore/100"
        }

        val displayPayee = payeeName?.takeIf { it.isNotBlank() } ?: vpa.substringBefore("@")
        val formattedAmount = amount?.takeIf { it > 0 }?.let { formatInr(it) } ?: "UPI Check"

        val reasonText = buildString {
            append(primaryTrigger)
            if (!secondaryTrigger.isNullOrBlank()) {
                append(" • ")
                append(secondaryTrigger)
            }
        }

        val remoteViews = RemoteViews(context.packageName, R.layout.notification_fraud_notch).apply {
            setTextViewText(R.id.notch_header, "UFO UPI GUARD • $appLabel • $engineTag")
            setTextViewText(R.id.notch_risk_badge, badgeText)
            setInt(R.id.notch_risk_badge, "setBackgroundResource", badgeRes)
            setTextViewText(R.id.notch_vpa, "$displayPayee ($vpa)")
            setTextViewText(R.id.notch_amount, formattedAmount)
            setTextViewText(R.id.notch_reason, reasonText)
            setTextViewText(
                R.id.btn_abort_payment,
                if (riskScore >= 60) "Abort & Review" else "Open Details"
            )

            setOnClickPendingIntent(R.id.btn_abort_payment, abortPendingIntent)
            setOnClickPendingIntent(R.id.btn_dismiss_notch, dismissPendingIntent)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_ufo)
            .setContentTitle("$displayPayee • $badgeText")
            .setContentText("$vpa • $formattedAmount — $reasonText")
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setCustomHeadsUpContentView(remoteViews)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVibrate(longArrayOf(0, 250, 120, 250))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(abortPendingIntent)

        notificationManager.notify(notificationId, builder.build())
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    private fun formatSourceAppName(pkgOrName: String?): String {
        if (pkgOrName.isNullOrBlank()) return "UPI App"
        return when (pkgOrName) {
            "com.google.android.apps.nbu.paisa.user" -> "Google Pay"
            "com.phonepe.app" -> "PhonePe"
            "net.one97.paytm" -> "Paytm"
            "in.org.npci.upiapp" -> "BHIM"
            "in.amazon.mShop.android.shopping" -> "Amazon Pay"
            "com.dreamplug.androidapp" -> "CRED"
            "com.whatsapp" -> "WhatsApp Pay"
            else -> pkgOrName.substringAfterLast(".")
        }
    }

    private fun formatInr(amount: Double): String {
        val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
        fmt.maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
        return "₹${fmt.format(amount)}"
    }
}
