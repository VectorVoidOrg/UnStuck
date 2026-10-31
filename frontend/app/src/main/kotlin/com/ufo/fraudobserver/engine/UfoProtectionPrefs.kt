package com.ufo.fraudobserver.engine

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the user's choice for whether the real-time background UPI screen extraction
 * and Truecaller-style risk popup facility is enabled or paused.
 */
object UfoProtectionPrefs {
    private const val PREFS_NAME = "ufo_protection_prefs"
    private const val KEY_BACKGROUND_GUARD_ENABLED = "background_guard_enabled"
    private const val KEY_SHOW_SAFE_POPUPS = "show_safe_popups"

    private val _isGuardEnabled = MutableStateFlow(true)
    val isGuardEnabled: StateFlow<Boolean> = _isGuardEnabled.asStateFlow()

    private val _showPopupForSafe = MutableStateFlow(true)
    val showPopupForSafe: StateFlow<Boolean> = _showPopupForSafe.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isGuardEnabled.value = prefs.getBoolean(KEY_BACKGROUND_GUARD_ENABLED, true)
        _showPopupForSafe.value = prefs.getBoolean(KEY_SHOW_SAFE_POPUPS, true)
    }

    fun isBackgroundGuardEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_BACKGROUND_GUARD_ENABLED, true)
        _isGuardEnabled.value = enabled
        return enabled
    }

    fun setBackgroundGuardEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BACKGROUND_GUARD_ENABLED, enabled)
            .apply()
        _isGuardEnabled.value = enabled
    }

    fun shouldShowPopupForSafe(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val show = prefs.getBoolean(KEY_SHOW_SAFE_POPUPS, true)
        _showPopupForSafe.value = show
        return show
    }

    fun setShowPopupForSafe(context: Context, show: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_SAFE_POPUPS, show)
            .apply()
        _showPopupForSafe.value = show
    }
}
