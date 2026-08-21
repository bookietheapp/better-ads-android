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

    /** Current ads backend: `GET /api/v1/serve?size={format}` (+ optional `app=` while unauthenticated). */
    SERVE_V1,

    /** Future dedicated ads API: `GET /ads/{format}`. */
    DEDICATED_API,
}

/**
 * Configuration for the Better Ads SDK.
 *
 * [SERVE_V1][BetterAdsContentMode.SERVE_V1] owns its fetch URL inside the SDK — hosts never pass a base URL
 * for that mode. [baseUrl] is only for legacy [BOOKIE_GET_AD][BetterAdsContentMode.BOOKIE_GET_AD] /
 * [DEDICATED_API][BetterAdsContentMode.DEDICATED_API].
 * [apiKey] is sent as `X-API-Key` when non-empty (optional today; required once the backend enforces auth).
 *
 * Identity defaults (recommended):
 * - omit [deviceId] → SDK persists an install UUID after [BetterAds.initialize]
 * - omit [sessionId] → SDK generates / rotates session on logout via [BetterAdsClient.setUserId]
 * - set [userId] when logged in, or call [BetterAdsClient.setUserId] later
 */
data class BetterAdsConfiguration(
    val apiKey: String = "",
    val contentMode: BetterAdsContentMode = BetterAdsContentMode.FIXTURE,
    /** Legacy remote base URL for BOOKIE_GET_AD / DEDICATED_API. Ignored for SERVE_V1 / FIXTURE. */
    val baseUrl: String? = null,
    /**
     * Transitional app identifier for [BetterAdsContentMode.SERVE_V1] (`app` query).
     * Omit once the API key identifies the host.
     */
    val appName: String? = null,
    /** Optional override. When null, the SDK owns session id lifecycle. */
    val sessionId: String? = null,
    /** Initial authenticated account id; omit when logged out. */
    val userId: String? = null,
    /** Optional override. When null, the SDK persists an install-scoped device id. */
    val deviceId: String? = null,
    val locale: Locale = Locale.getDefault(),
) {
    /** Resolved HTTP base URL for the active content mode. */
    internal fun resolvedBaseUrl(): String? = when (contentMode) {
        BetterAdsContentMode.SERVE_V1 -> BetterAdsEndpoints.SERVE_V1_BASE_URL
        BetterAdsContentMode.BOOKIE_GET_AD,
        BetterAdsContentMode.DEDICATED_API,
        -> baseUrl
        BetterAdsContentMode.FIXTURE -> null
    }

    companion object {
        fun fixture(
            apiKey: String,
            userId: String? = null,
            locale: Locale = Locale.getDefault(),
        ): BetterAdsConfiguration = BetterAdsConfiguration(
            apiKey = apiKey,
            contentMode = BetterAdsContentMode.FIXTURE,
            userId = userId,
            locale = locale,
        )
    }
}

/** SDK-owned ads backend endpoints. Host apps never configure these URLs. */
internal object BetterAdsEndpoints {
    const val SERVE_V1_BASE_URL = "https://us-central1-better-ads-501813.cloudfunctions.net"
    const val EVENTS_PATH = "/api/v1/events"
}

/** Optional auth for remote modes (e.g. Bearer for legacy getAd). */
fun interface BetterAdsAuthProviding {
    suspend fun bearerAccessToken(): String?
}
