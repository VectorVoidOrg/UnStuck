package com.ufo.fraudobserver.engine

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.BitSet
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Layer 1: Zero-Latency Local Bloom Filter
 * Specifications from PRD:
 * - Capacity (n): 100,000 entries
 * - False Positive Probability (p): 0.001 (0.1%)
 * - Bit Array Size (m): ~1.44 MB (11,981,332 bits)
 * - Hash Functions (k): 10 independent hashes derived via double hashing:
 *     h_i(x) = (h1(x) + i * h2(x)) mod m
 * - Stored in memory via BitSet and persisted to context.filesDir/bloom_filter.bin
 */
class BloomFilterEngine(private val context: Context) {

    companion object {
        private const val TAG = "BloomFilterEngine"
        const val BIT_ARRAY_SIZE = 11_981_332 // m ~ 1.44 MB
        const val HASH_COUNT = 10             // k = 10
        private const val SEED1 = 0x7F4A7C15
        private const val SEED2 = 0x1B873593
        private const val FILE_NAME = "bloom_filter.bin"

        @Volatile
        private var INSTANCE: BloomFilterEngine? = null

        fun getInstance(context: Context): BloomFilterEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BloomFilterEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val bitSet = BitSet(BIT_ARRAY_SIZE)
    private val entryCount = AtomicInteger(0)
    private val storageFile = File(context.filesDir, FILE_NAME)

    init {
        loadFromDisk()
        seedKnownScammers()
    }

    /**
     * Sub-millisecond O(1) query against local fraudulent VPAs without network overhead.
     */
    fun contains(vpa: String): Boolean {
        if (vpa.isBlank()) return false
        val normalized = normalizeVpa(vpa)
        val h1 = MurmurHash3.hash32(normalized, SEED1)
        val h2 = MurmurHash3.hash32(normalized, SEED2)

        for (i in 0 until HASH_COUNT) {
            val combined = (h1.toLong() + i.toLong() * h2.toLong()) and 0x7FFFFFFF_FFFFFFFFL
            val index = (combined % BIT_ARRAY_SIZE).toInt()
            if (!bitSet.get(index)) {
                return false
            }
        }
        return true
    }

    /**
     * Inserts a known fraudulent VPA and updates bit array.
     */
    @Synchronized
    fun add(vpa: String) {
        if (vpa.isBlank()) return
        val normalized = normalizeVpa(vpa)
        val h1 = MurmurHash3.hash32(normalized, SEED1)
        val h2 = MurmurHash3.hash32(normalized, SEED2)

        for (i in 0 until HASH_COUNT) {
            val combined = (h1.toLong() + i.toLong() * h2.toLong()) and 0x7FFFFFFF_FFFFFFFFL
            val index = (combined % BIT_ARRAY_SIZE).toInt()
            bitSet.set(index, true)
        }
        entryCount.incrementAndGet()
    }

    /**
     * Bulk insertion of newly blacklisted VPAs from delta sync.
     */
    @Synchronized
    fun addAll(vpas: List<String>) {
        vpas.forEach { add(it) }
        saveToDisk()
    }

    fun getEstimatedCount(): Int = entryCount.get()

    private fun normalizeVpa(vpa: String): String {
        return vpa.trim().lowercase(Locale.ROOT)
    }

    @Synchronized
    fun saveToDisk() {
        try {
            val bytes = bitSet.toByteArray()
            FileOutputStream(storageFile).use { fos ->
                fos.write(bytes)
                fos.flush()
            }
            Log.d(TAG, "Bloom filter bitset saved (${bytes.size} bytes).")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist bloom filter", e)
        }
    }

    private fun loadFromDisk() {
        if (storageFile.exists()) {
            try {
                val bytes = ByteArray(storageFile.length().toInt())
                FileInputStream(storageFile).use { fis ->
                    fis.read(bytes)
                }
                val loadedBitSet = BitSet.valueOf(bytes)
                for (i in 0 until BIT_ARRAY_SIZE) {
                    if (loadedBitSet.get(i)) {
                        bitSet.set(i, true)
                    }
                }
                Log.d(TAG, "Bloom filter loaded from disk (${bytes.size} bytes).")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read bloom filter from disk, reinitializing", e)
            }
        }
    }

    private fun seedKnownScammers() {
        // High-profile static fraudulent VPAs from PRD
        val seeds = listOf(
            "pmcares.refund@okaxis",
            "irctcc.support@ybl",
            "bescom.electricity.bill@paytm",
            "lottery.prize2026@sbi",
            "sbi.customercare.kyc@upi",
            "flipkart.refund@okaxis",
            "refund.customer.desk@icici",
            "kyc.update.support@paytm",
            "phonepe.rewards2026@ybl"
        )
        seeds.forEach { add(it) }
    }
}

