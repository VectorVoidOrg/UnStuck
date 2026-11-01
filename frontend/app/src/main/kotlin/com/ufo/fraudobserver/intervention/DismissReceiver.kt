package com.ufo.fraudobserver.intervention

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Handles DISMISS button tap on Heads-Up Notch Notification.
 * Cancels the alert notification immediately.
 */
class DismissReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DismissReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val vpa = intent.getStringExtra("EXTRA_VPA") ?: return
        val notificationId = intent.getIntExtra("NOTIFICATION_ID", -1)

        Log.d(TAG, "User dismissed fraud alert for VPA: $vpa")

        if (notificationId != -1) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }
    }
}

