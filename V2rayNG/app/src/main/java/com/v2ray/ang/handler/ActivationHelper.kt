package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AngApplication

/**
 * Activation attempt lockout stored in [SharedPreferences] (per user request).
 */
object ActivationHelper {

    private const val PREFS_NAME = "azatnet_activation"
    private const val KEY_FAIL_COUNT = "fail_count"
    private const val KEY_LOCK_UNTIL_MS = "lock_until_ms"

    private const val MAX_ATTEMPTS = 3
    private const val LOCK_MS = 60 * 60 * 1000L

    private fun prefs(): android.content.SharedPreferences =
        AngApplication.application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isLocked(): Boolean {
        val until = prefs().getLong(KEY_LOCK_UNTIL_MS, 0L)
        return System.currentTimeMillis() < until
    }

    fun lockRemainingMs(): Long {
        val until = prefs().getLong(KEY_LOCK_UNTIL_MS, 0L)
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun recordFailedAttempt() {
        val p = prefs()
        val n = p.getInt(KEY_FAIL_COUNT, 0) + 1
        p.edit().putInt(KEY_FAIL_COUNT, n).apply()
        if (n >= MAX_ATTEMPTS) {
            p.edit()
                .putLong(KEY_LOCK_UNTIL_MS, System.currentTimeMillis() + LOCK_MS)
                .putInt(KEY_FAIL_COUNT, 0)
                .apply()
        }
    }

    fun resetAttempts() {
        prefs().edit().putInt(KEY_FAIL_COUNT, 0).apply()
    }
}
