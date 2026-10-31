package com.ufo.fraudobserver.intervention

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ufo.fraudobserver.engine.SessionWhitelist

/**
 * Handles DISMISS button tap on Heads-Up Notch Notification.
 * Puts the VPA into the 5-minute session whitelist and cancels the alert.
 */
class DismissReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DismissReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val vpa = intent.getStringExtra("EXTRA_VPA") ?: return
        val notificationId = intent.getIntExtra("NOTIFICATION_ID", -1)

        Log.d(TAG, "User dismissed fraud alert for VPA: $vpa. Adding to 5-minute session whitelist.")
        SessionWhitelist.add(vpa)

        if (notificationId != -1) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }
    }
}

