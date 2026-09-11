package com.betterads.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.betterads.BetterAdsClient
import com.betterads.AdLog
import com.betterads.AdResponseCache
import com.betterads.ExternalAdId
import com.betterads.isNoEligibleAd
import com.betterads.model.AdCtaAction
import com.betterads.model.AdFormat
import com.betterads.model.AdModel
import com.betterads.model.AdType
import com.betterads.model.BetterAdsError

/**
 * Loads ad content and owns impression / click reporting for a single placement.
 * Matches iOS `AdViewModel`.
 *
 * Creative selection is owned by the serve API: load once, revalidate only when
 * the host starts a new screen session, keep the current creative while fetching,
 * and only swap UI when the payload changes.
 */
class AdViewModel(
    private val client: BetterAdsClient,
    private val type: AdType,
    preloadedAd: AdModel? = null,
    externalAdId: String? = null,
) {
    private val externalAdId: String? = ExternalAdId.normalize(externalAdId)
    sealed interface State {
        data object Idle : State
        data object Loading : State
        data class Loaded(val ad: AdModel) : State
        data class Failed(val message: String) : State
    }

    var state: State by mutableStateOf(
        when {
            preloadedAd != null -> State.Loaded(preloadedAd)
            // Paint cached creative immediately so remounts don't flash a blank loading slot.
            else -> client.cachedAd(type, this.externalAdId)?.let { State.Loaded(it) } ?: State.Idle
        },
    )
        private set

    private var didTrackImpression = false
    private var isRevalidating = false

    val ad: AdModel?
        get() = (state as? State.Loaded)?.ad

    private fun placementIdentity(): String = AdResponseCache.key(type, externalAdId)

    /**
     * Fetches only when this slot has nothing to show. Scroll off/on and lazy
     * remounts reuse the cached creative — they do not hit Serve again.
     */
    suspend fun loadIfNeeded() {
        when (state) {
            is State.Loaded, is State.Failed -> return
            State.Idle, State.Loading -> revalidate()
        }
    }

    /**
     * Asks the serve API whether this slot should keep or replace its creative.
     *
     * Keeps the current creative visible while fetching (no flash).
     * Updates state only when the API returns a different payload.
     * Resets impression eligibility when `adId` changes.
     */
    suspend fun revalidate() {
        if (isRevalidating) return
        isRevalidating = true
        try {
            val previous = ad
            val hadContent = previous != null
            // Only show the blank loading placeholder when we have nothing to display yet.
            if (!hadContent) {
                state = State.Loading
            }

            try {
                applyServeResult(previous = previous, fresh = client.fetchAd(type, externalAdId))
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Lazy lists may cancel after fetch; creative is still in the client cache.
                if (!hadContent) {
                    client.cachedAd(type, externalAdId)?.let { cached ->
                        state = State.Loaded(cached)
                    } ?: run {
                        state = State.Idle
                    }
                }
                throw e
            } catch (e: Exception) {
                val message = (e as? BetterAdsError)?.message ?: e.message ?: e.toString()
                // Keyed 404 is a definitive miss — hide the slot. Do not keep a previous
                // creative and do not retry as unkeyed Serve.
                if (externalAdId != null && isNoEligibleAd(e)) {
                    state = State.Failed(message)
                } else if (!hadContent) {
                    state = State.Failed(message)
                }
            }
        } finally {
            isRevalidating = false
        }
    }

    /**
     * Called after Bookie-parity viewability (50% + 200 ms dwell). Fires at most
     * once per `adId` per host screen session, even if the placement remounts.
     * @return true when an impression was newly tracked.
     */
    fun trackImpressionIfNeeded(sessionId: String? = null): Boolean {
        val current = ad ?: return false
        if (state !is State.Loaded) return false
        if (!client.impressionLedger.consume(placementIdentity(), current.adId)) {
            AdLog.i("impression skipped already recorded adId=${current.adId} size=${type.rawValue}")
            return false
        }
        didTrackImpression = true
        AdLog.i("impression adId=${current.adId} size=${type.rawValue}")
        client.trackImpression(current.adId)
        return true
    }

    /** Allows another impression after the host starts a new screen session. */
    fun resetImpressionEligibility() {
        ad?.let { client.impressionLedger.release(placementIdentity(), it.adId) }
        didTrackImpression = false
        AdLog.i("impression session reset size=${type.rawValue}")
    }

    fun handleClick(): AdCtaAction? {
        val current = ad ?: return null
        AdLog.i("click adId=${current.adId} cta=${current.ctaLink}")
        client.trackClick(current.adId, current.ctaLink)
        return current.ctaAction
    }

    private fun applyServeResult(previous: AdModel?, fresh: AdModel) {
        if (previous == fresh) return
        if (previous?.adId != fresh.adId) {
            didTrackImpression = false
        }
        state = State.Loaded(fresh)
    }

    companion object {
        fun forFormat(
            client: BetterAdsClient,
            format: AdFormat,
            externalAdId: String? = null,
        ): AdViewModel = AdViewModel(client = client, type = AdType(format), externalAdId = externalAdId)
    }
}
