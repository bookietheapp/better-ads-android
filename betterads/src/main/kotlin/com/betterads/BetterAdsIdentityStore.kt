package com.betterads

import android.content.Context
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns ads identity used on impression/click payloads.
 *
 * By default the SDK manages `device_id` (persisted after [BetterAds.initialize]) and
 * `session_id` (process-scoped). Hosts only need [BetterAdsClient.setUserId] on auth changes.
 */
internal class BetterAdsIdentityStore(
    deviceId: String?,
    sessionId: String?,
    userId: String?,
) {
    private val ownsSession: Boolean = sessionId == null
    private val deviceIdValue: String = resolveDeviceId(deviceId)
    private val sessionIdRef = AtomicReference(sessionId ?: UUID.randomUUID().toString())
    private val userIdRef = AtomicReference(userId)

    fun setUserId(userId: String?) {
        val previous = userIdRef.getAndSet(userId)
        // Logout / clear account → new visit session (only when SDK owns session id).
        if (ownsSession && previous != null && userId == null) {
            sessionIdRef.set(UUID.randomUUID().toString())
        }
    }

    fun snapshot(): IdentitySnapshot = IdentitySnapshot(
        deviceId = deviceIdValue,
        sessionId = sessionIdRef.get(),
        userId = userIdRef.get(),
    )

    data class IdentitySnapshot(
        val deviceId: String,
        val sessionId: String,
        val userId: String?,
    )

    companion object {
        private const val PREFS_NAME = "com.betterads.identity"
        private const val DEVICE_ID_KEY = "device_id"

        private fun resolveDeviceId(override: String?): String {
            val trimmed = override?.trim().orEmpty()
            if (trimmed.isNotEmpty()) return trimmed

            val context = BetterAds.appContext
            if (context != null) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val existing = prefs.getString(DEVICE_ID_KEY, null)?.trim().orEmpty()
                if (existing.isNotEmpty()) return existing
                val created = UUID.randomUUID().toString()
                prefs.edit().putString(DEVICE_ID_KEY, created).apply()
                return created
            }

            // Tests / hosts that skip initialize — ephemeral install id for this process.
            return UUID.randomUUID().toString()
        }
    }
}

/**
 * One-time Android setup so the SDK can persist `device_id`.
 * Call from `Application.onCreate` before showing ads.
 */
object BetterAds {
    @Volatile
    internal var appContext: Context? = null
        private set

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }
}
