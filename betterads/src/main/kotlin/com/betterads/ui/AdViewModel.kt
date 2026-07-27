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
        if (preloadedAd != null) State.Loaded(preloadedAd) else State.Idle,
    )
        private set

    private var didTrackImpression = false

    val ad: AdModel?
        get() = (state as? State.Loaded)?.ad

    suspend fun loadIfNeeded() {
        when (state) {
            State.Loading, is State.Loaded -> return
            State.Idle, is State.Failed -> Unit
        }
        state = State.Loading
        state = try {
            State.Loaded(client.fetchAd(type))
        } catch (e: Exception) {
            val message = (e as? BetterAdsError)?.message ?: e.message ?: e.toString()
            State.Failed(message)
        }
    }

    /** @return true when an impression was newly tracked. */
    fun trackImpressionIfNeeded(): Boolean {
        if (state !is State.Loaded || didTrackImpression) return false
        didTrackImpression = true
        client.trackImpression(type)
        return true
    }

    fun handleClick(): AdCtaAction? {
        val current = ad ?: return null
        client.trackClick(type, current.cta.action.value)
        return current.cta.action
    }

    companion object {
        fun forFormat(client: BetterAdsClient, format: AdFormat): AdViewModel =
            AdViewModel(client = client, type = AdType(format))
    }
}
