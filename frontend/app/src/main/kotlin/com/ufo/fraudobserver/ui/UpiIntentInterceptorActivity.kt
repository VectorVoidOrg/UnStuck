package com.ufo.fraudobserver.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.ufo.fraudobserver.UfoMainActivity
import com.ufo.fraudobserver.engine.RiskEvaluator
import com.ufo.fraudobserver.engine.RiskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Module A.3: Deep Link & QR Interceptor
 * Intercepts upi://pay intents before the user reaches the UPI payment app.
 */
class UpiIntentInterceptorActivity : Activity() {

    companion object {
        private const val TAG = "UpiIntentInterceptor"
    }

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri: Uri? = intent.data
        if (uri == null || uri.scheme != "upi" || uri.host != "pay") {
            finish()
            return
        }

        val payeeVpa = uri.getQueryParameter("pa")
        val payeeName = uri.getQueryParameter("pn")
        val amountStr = uri.getQueryParameter("am")
        val merchantCode = uri.getQueryParameter("mc")
        val amount = amountStr?.toDoubleOrNull()

        if (payeeVpa.isNullOrBlank()) {
            forwardDirectlyToUpi(uri)
            finish()
            return
        }

        Log.i(TAG, "Intercepted UPI Intent: pa=$payeeVpa, pn=$payeeName, am=$amount, mc=$merchantCode")

        val evaluator = RiskEvaluator.getInstance(applicationContext)

        activityScope.launch {
            val result = withContext(Dispatchers.Default) {
                evaluator.evaluate(
                    vpa = payeeVpa,
                    payeeName = payeeName,
                    amount = amount,
                    isCollectRequest = false,
                    contextText = "Deep Link / QR Intent: mc=$merchantCode",
                    sourceApp = "IntentInterceptor",
                    triggerIntervention = true
                )
            }

            if (result.status == RiskStatus.FRAUD || result.status == RiskStatus.WARNING) {
                Toast.makeText(
                    this@UpiIntentInterceptorActivity,
                    "⚠️ UFO INTERCEPTED HIGH-RISK UPI INTENT!",
                    Toast.LENGTH_LONG
                ).show()

                // Route to UFO Safety Detail Screen
                val alertIntent = Intent(this@UpiIntentInterceptorActivity, UfoMainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("NAVIGATE_TO", "ALERT_DETAIL")
                    putExtra("EXTRA_VPA", payeeVpa)
                    putExtra("EXTRA_PAYEE", payeeName)
                    putExtra("EXTRA_AMOUNT", amount ?: 0.0)
                    putExtra("EXTRA_RISK_SCORE", result.riskScore)
                    putExtra("EXTRA_TRIGGER", result.triggers.joinToString(" • "))
                    putExtra("ORIGINAL_URI", uri.toString())
                }
                startActivity(alertIntent)
                finish()
            } else {
                // Safe transaction: Forward to user's UPI app
                forwardDirectlyToUpi(uri)
                finish()
            }
        }
    }

    private fun forwardDirectlyToUpi(uri: Uri) {
        try {
            val forwardIntent = Intent(Intent.ACTION_VIEW, uri)
            // Exclude our own package to prevent loop
            val chooser = Intent.createChooser(forwardIntent, "Complete payment with:")
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(this, "No UPI application installed to handle payment.", Toast.LENGTH_SHORT).show()
        }
    }
}

