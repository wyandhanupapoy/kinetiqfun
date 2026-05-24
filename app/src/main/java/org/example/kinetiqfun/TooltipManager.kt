package org.example.kinetiqfun

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages the state of tooltips (which ones have been seen)
 */
class TooltipManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("tooltip_prefs", Context.MODE_PRIVATE)

    /**
     * Returns true if the tooltip with the given key has not been shown yet
     */
    fun shouldShowTooltip(key: String): Boolean {
        return !prefs.getBoolean(key, false)
    }

    /**
     * Marks the tooltip with the given key as seen
     */
    fun markTooltipAsSeen(key: String) {
        prefs.edit().putBoolean(key, true).apply()
    }
}