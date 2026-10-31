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

/**
 * Module C: Non-Blocking Intervention UI (The "Notch" Prompt)
 * To avoid Android 12+ Untrusted Touch blocking, SYSTEM_ALERT_WINDOW overlays are strictly prohibited.
 * Interventions are delivered via a high-priority heads-up notification rendered by SystemUI.
 */
class InterventionNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "ufo_critical_alerts"
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
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_critical_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_critical_alerts_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(alarmSound, audioAttributes)
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
        amount: Double?
    ) {
        val notificationId = NOTIFICATION_ID_BASE + (vpa.hashCode() and 0xFFFF)

        // 1. Primary Action Intent: ABORT PAYMENT -> Pulls user out of UPI app to UFO Safety Review
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

        // 2. Secondary Action Intent: DISMISS -> Session Whitelist for 5 minutes
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

        // 3. Custom RemoteViews Layout (notification_fraud_notch.xml)
        val remoteViews = RemoteViews(context.packageName, R.layout.notification_fraud_notch).apply {
            setTextViewText(R.id.notch_header, "⚠️ CRITICAL RISK: DO NOT ENTER PIN")
            setTextViewText(R.id.notch_risk_badge, "RISK: $riskScore%")
            setTextViewText(R.id.notch_vpa, "Receiver: $vpa")

            val reasonText = if (secondaryTrigger.isNullOrBlank()) {
                "Reason: $primaryTrigger"
            } else {
                "Reason: $primaryTrigger • $secondaryTrigger"
            }
            setTextViewText(R.id.notch_reason, reasonText)

            setOnClickPendingIntent(R.id.btn_abort_payment, abortPendingIntent)
            setOnClickPendingIntent(R.id.btn_dismiss_notch, dismissPendingIntent)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setCustomHeadsUpContentView(remoteViews)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(abortPendingIntent)

        notificationManager.notify(notificationId, builder.build())
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
}

