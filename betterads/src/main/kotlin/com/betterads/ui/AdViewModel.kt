package com.betterads.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.betterads.BetterAdsClient
import com.betterads.model.AdCtaAction
import com.betterads.model.AdFormat
import com.betterads.model.AdModel
import com.betterads.model.AdType
import com.betterads.model.BetterAdsError

/**
 * Loads ad content and owns impression / click reporting for a single placement.
 * Matches iOS `AdViewModel`.
 *
 * Creative selection is owned by the serve API: revalidate on appear / host surface
 * refresh, keep the current creative while fetching, and only swap UI when the
 * payload changes.
 */
class AdViewModel(
    private val client: BetterAdsClient,
    private val type: AdType,
    preloadedAd: AdModel? = null,
) {
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
            else -> client.cachedAd(type)?.let { State.Loaded(it) } ?: State.Idle
        },
    )
        private set

    private var didTrackImpression = false
    private var isRevalidating = false

    val ad: AdModel?
        get() = (state as? State.Loaded)?.ad

    /**
     * Asks the serve API whether this slot should keep or replace its creative.
     *
     * Keeps the current creative visible while fetching (no flash).
     * Updates state only when the API returns a different payload.
     * Resets impression eligibility when `campaignId` changes.
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
                applyServeResult(previous = previous, fresh = client.fetchAd(type))
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Lazy lists may cancel after fetch; creative is still in the client cache.
                if (!hadContent) {
                    client.cachedAd(type)?.let { cached ->
                        state = State.Loaded(cached)
                    } ?: run {
                        state = State.Idle
                    }
                }
                throw e
            } catch (e: Exception) {
                if (!hadContent) {
                    val message = (e as? BetterAdsError)?.message ?: e.message ?: e.toString()
                    state = State.Failed(message)
                }
            }
        } finally {
            isRevalidating = false
        }
    }

    /** Backward-compatible alias used by older call sites / tests. */
    suspend fun loadIfNeeded() = revalidate()

    /** @return true when an impression was newly tracked. */
    fun trackImpressionIfNeeded(): Boolean {
        if (state !is State.Loaded || didTrackImpression) return false
        val current = ad ?: return false
        didTrackImpression = true
        client.trackImpression(current.campaignId)
        return true
    }

    fun handleClick(): AdCtaAction? {
        val current = ad ?: return null
        client.trackClick(current.campaignId, current.cta.action.value)
        return current.cta.action
    }

    private fun applyServeResult(previous: AdModel?, fresh: AdModel) {
        if (previous == fresh) return
        if (previous?.campaignId != fresh.campaignId) {
            didTrackImpression = false
        }
        state = State.Loaded(fresh)
    }

    companion object {
        fun forFormat(client: BetterAdsClient, format: AdFormat): AdViewModel =
            AdViewModel(client = client, type = AdType(format))
    }
}
