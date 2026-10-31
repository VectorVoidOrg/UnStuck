package com.ufo.fraudobserver.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Layer 3: Live Call / Vishing Telemetry
 * Monitors if the user is in an active phone call (CALL_STATE_OFFHOOK).
 * Most high-value UPI scams occur while the scammer is coaching the victim over a voice call.
 */
class CallStateMonitor(private val context: Context) {

    companion object {
        private const val TAG = "CallStateMonitor"

        @Volatile
        private var INSTANCE: CallStateMonitor? = null

        fun getInstance(context: Context): CallStateMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CallStateMonitor(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val isCallActiveState = AtomicBoolean(false)

    init {
        registerCallListener()
    }

    fun isCallActive(): Boolean {
        // Double check via direct state query if permission granted
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            telephonyManager?.let { tm ->
                if (tm.callState == TelephonyManager.CALL_STATE_OFFHOOK) {
                    isCallActiveState.set(true)
                }
            }
        }
        return isCallActiveState.get()
    }

    private fun registerCallListener() {
        if (telephonyManager == null) return

        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted. Active call telemetry limited.")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyManager.registerTelephonyCallback(
                    context.mainExecutor,
                    object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            val active = (state == TelephonyManager.CALL_STATE_OFFHOOK)
                            isCallActiveState.set(active)
                            Log.d(TAG, "CallStateListener (API 31+): active=$active")
                        }
                    }
                )
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.listen(
                    object : PhoneStateListener() {
                        @Deprecated("Deprecated in Java")
                        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                            val active = (state == TelephonyManager.CALL_STATE_OFFHOOK)
                            isCallActiveState.set(active)
                            Log.d(TAG, "PhoneStateListener (legacy): active=$active")
                        }
                    },
                    PhoneStateListener.LISTEN_CALL_STATE
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register call state listener", e)
        }
    }
}

