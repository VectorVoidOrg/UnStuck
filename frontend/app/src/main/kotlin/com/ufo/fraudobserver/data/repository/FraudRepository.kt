package com.ufo.fraudobserver.data.repository

import android.content.Context
import android.util.Log
import com.ufo.fraudobserver.data.local.AppDatabase
import com.ufo.fraudobserver.data.local.FlaggedTransactionEntity
import com.ufo.fraudobserver.data.local.WhitelistedVpaEntity
import com.ufo.fraudobserver.data.remote.EvaluateRequest
import com.ufo.fraudobserver.data.remote.ReportRequest
import com.ufo.fraudobserver.data.remote.UfoApiService
import com.ufo.fraudobserver.engine.BloomFilterEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FraudRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val bloomFilter: BloomFilterEngine = BloomFilterEngine.getInstance(context),
    private val apiService: UfoApiService = UfoApiService.create()
) {

    companion object {
        private const val TAG = "FraudRepository"
    }

    val flaggedTransactions: Flow<List<FlaggedTransactionEntity>> =
        database.flaggedTransactionDao().getAllFlow()

    val whitelistedVpas: Flow<List<WhitelistedVpaEntity>> =
        database.whitelistedVpaDao().getAllFlow()

    val totalThreatCount: Flow<Int> =
        database.flaggedTransactionDao().getCountFlow()

    suspend fun cleanLegacySeededDataIfNeeded() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("ufo_sync_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("legacy_demo_activity_cleared", false)) {
            database.flaggedTransactionDao().clearAll()
            prefs.edit()
                .putBoolean("legacy_demo_activity_cleared", true)
                .remove("initial_activity_seeded")
                .apply()
        }
    }

    suspend fun deleteTransaction(id: Long) = withContext(Dispatchers.IO) {
        database.flaggedTransactionDao().deleteById(id)
    }

    suspend fun recordFlaggedTransaction(
        vpa: String,
        payeeName: String?,
        amount: Double?,
        riskScore: Int,
        triggerReason: String,
        sourceApp: String?
    ): Long = withContext(Dispatchers.IO) {
        val entity = FlaggedTransactionEntity(
            vpa = vpa,
            payeeName = payeeName,
            amount = amount,
            riskScore = riskScore,
            triggerReason = triggerReason,
            sourceApp = sourceApp
        )
        database.flaggedTransactionDao().insert(entity)
    }

    suspend fun addToWhitelist(vpa: String) = withContext(Dispatchers.IO) {
        database.whitelistedVpaDao().insert(WhitelistedVpaEntity(vpa = vpa))
    }

    suspend fun isWhitelisted(vpa: String): Boolean = withContext(Dispatchers.IO) {
        database.whitelistedVpaDao().isWhitelisted(vpa)
    }

    suspend fun removeFromWhitelist(vpa: String) = withContext(Dispatchers.IO) {
        database.whitelistedVpaDao().remove(vpa)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        database.flaggedTransactionDao().clearAll()
    }

    suspend fun submitScamReport(
        vpa: String,
        category: String,
        notes: String?,
        sourceApp: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Flip bits locally immediately in Bloom Filter
            bloomFilter.add(vpa)
            bloomFilter.saveToDisk()

            val response = apiService.reportScam(
                ReportRequest(
                    vpa = vpa,
                    scamCategory = category,
                    sourceApp = sourceApp,
                    notes = notes
                )
            )

            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.message)
            } else {
                Result.success("Report saved locally to blacklist (backend offline)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Backend reporting failed, saved locally: ${e.message}")
            Result.success("Report saved locally to local fraud filter")
        }
    }

    suspend fun syncBlacklistDelta(): Int = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("ufo_sync_prefs", Context.MODE_PRIVATE)
            val lastSync = prefs.getLong("last_delta_sync_timestamp", 0L)

            val response = apiService.getBlacklistDelta(since = lastSync)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val newVpas = body.blacklistedVpas
                if (newVpas.isNotEmpty()) {
                    bloomFilter.addAll(newVpas)
                    Log.d(TAG, "Synced ${newVpas.size} new blacklisted VPAs into Bloom filter")
                }
                prefs.edit().putLong("last_delta_sync_timestamp", body.timestamp).apply()
                return@withContext newVpas.size
            }
        } catch (e: Exception) {
            Log.w(TAG, "Blacklist delta sync failed: ${e.message}")
        }
        0
    }
}

