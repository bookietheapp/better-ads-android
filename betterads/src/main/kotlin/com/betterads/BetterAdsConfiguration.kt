package com.betterads

import java.util.Locale

/**
 * Where the SDK loads creatives from until the dedicated ads backend ships.
 * Matches iOS `BetterAdsContentMode`.
 */
enum class BetterAdsContentMode {
    /** Offline / spike: built-in sample creatives, no network. */
    FIXTURE,

    /** Interim: `GET /getAd?size={format}`. */
    BOOKIE_GET_AD,

    /** Future dedicated ads API: `GET /ads/{format}`. */
    DEDICATED_API,
}

/**
 * Configuration for the Better Ads SDK.
 * Host apps must supply [apiKey]. Remote [baseUrl] is only needed outside fixture mode.
 */
data class BetterAdsConfiguration(
    val apiKey: String,
    val contentMode: BetterAdsContentMode = BetterAdsContentMode.FIXTURE,
    val baseUrl: String? = null,
    val sessionId: String? = null,
    val userId: String? = null,
    val locale: Locale = Locale.getDefault(),
) {
    companion object {
        fun fixture(
            apiKey: String,
            locale: Locale = Locale.getDefault(),
        ): BetterAdsConfiguration = BetterAdsConfiguration(
            apiKey = apiKey,
            contentMode = BetterAdsContentMode.FIXTURE,
            locale = locale,
        )
    }
}

/** Optional auth for remote modes (e.g. Bearer for legacy getAd). */
fun interface BetterAdsAuthProviding {
    suspend fun bearerAccessToken(): String?
}
