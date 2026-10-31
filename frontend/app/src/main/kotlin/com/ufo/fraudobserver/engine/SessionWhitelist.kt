package com.ufo.fraudobserver.engine

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Temporary in-memory session whitelist for VPAs dismissed by user.
 * Preserves a 5-minute (300,000ms) cooldown so legitimate user transactions aren't spammed.
 */
object SessionWhitelist {

    private const val COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes
    private val whitelistMap = ConcurrentHashMap<String, Long>()

    fun add(vpa: String) {
        if (vpa.isBlank()) return
        val normalized = vpa.trim().lowercase(Locale.ROOT)
        whitelistMap[normalized] = System.currentTimeMillis()
    }

    fun isWhitelisted(vpa: String): Boolean {
        if (vpa.isBlank()) return false
        val normalized = vpa.trim().lowercase(Locale.ROOT)
        val addedTime = whitelistMap[normalized] ?: return false
        val elapsed = System.currentTimeMillis() - addedTime
        return if (elapsed < COOLDOWN_MS) {
            true
        } else {
            whitelistMap.remove(normalized)
            false
        }
    }

    fun remove(vpa: String) {
        whitelistMap.remove(vpa.trim().lowercase(Locale.ROOT))
    }

    fun clear() {
        whitelistMap.clear()
    }
}

