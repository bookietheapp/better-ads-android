package com.betterads

/**
 * Once-per-placement impression latch, independent of view-model lifetime
 * and of host session tokens.
 *
 * Lazy lists dispose rows when they leave the window. Host `remember` / `@State`
 * UUIDs are often new on remount — those are **not** a new screen visit. The
 * latch is keyed only by placement + ad id so scroll off/on does not re-count.
 *
 * A new visit / pull-to-refresh must [clear] or [release].
 */
internal class AdImpressionLedger {
    private val keys = HashSet<String>()

    @Synchronized
    fun consume(placement: String, adId: String): Boolean {
        return keys.add(key(placement, adId))
    }

    @Synchronized
    fun release(placement: String, adId: String) {
        keys.remove(key(placement, adId))
    }

    @Synchronized
    fun clear() {
        keys.clear()
    }

    private fun key(placement: String, adId: String) = "$placement|$adId"
}
