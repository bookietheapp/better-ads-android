package com.betterads

import android.util.Log

/** Logcat logs for host-app debugging (filter tag `BetterAds`). No-op in release. */
internal object AdLog {
    const val TAG = "BetterAds"

    fun i(message: String) {
        emit(message, isWarning = false)
    }

    fun w(message: String) {
        emit(message, isWarning = true)
    }

    private fun emit(message: String, isWarning: Boolean) {
        if (!BuildConfig.DEBUG) return
        println("[BetterAds] $message")
        try {
            if (isWarning) {
                Log.w(TAG, message)
            } else {
                Log.i(TAG, message)
            }
        } catch (_: RuntimeException) {
            // android.util.Log is unmocked in JVM unit tests.
        }
    }
}
