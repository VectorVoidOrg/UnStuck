package com.ufo.fraudobserver

import android.app.Application
import android.util.Log
import com.ufo.fraudobserver.engine.BloomFilterEngine
import com.ufo.fraudobserver.intervention.InterventionNotifier
import com.ufo.fraudobserver.sync.BlacklistSyncWorker

class UfoApplication : Application() {

    companion object {
        private const val TAG = "UfoApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Initializing UFO (Unified Fraud Observer)...")

        // Initialize local Bloom Filter Engine in memory
        BloomFilterEngine.getInstance(this)

        // Initialize high-urgency heads-up notification channel
        InterventionNotifier.getInstance(this)

        // Schedule periodic delta sync worker (every 6 hours)
        BlacklistSyncWorker.schedule(this)
    }
}

