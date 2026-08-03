package com.betterads

import com.betterads.model.AdFormat
import com.betterads.model.AdModel
import com.betterads.model.AdType
import com.betterads.model.BetterAdsError
import com.betterads.network.AdsApiClient
import com.betterads.network.HttpClient
import com.betterads.network.UrlConnectionHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger

internal fun interface BetterAdsContentProviding {
    suspend fun fetchAd(format: AdFormat): AdModel
}

internal class HttpBetterAdsContentProvider(
    private val api: AdsApiClient,
) : BetterAdsContentProviding {
    override suspend fun fetchAd(format: AdFormat): AdModel =
        api.fetchAd(AdType(format))
}

internal class FixtureBetterAdsContentProvider : BetterAdsContentProviding {
    override suspend fun fetchAd(format: AdFormat): AdModel {
        if (format == AdFormat.INTERSTITIAL) {
            throw BetterAdsError.UnknownAdType(AdType(format))
        }
        return AdModel.previewFixture(format)
    }
}

/**
 * Configured client used by [com.betterads.ui.BetterAdView].
 *
 * The SDK owns creative fetch, analytics POSTs, `device_id`, and `session_id`.
 * Hosts call [setUserId] on login/logout.
 */
class BetterAdsClient private constructor(
    private val api: AdsApiClient,
    private val identity: BetterAdsIdentityStore,
    private val contentProvider: BetterAdsContentProviding,
    private val contentMode: BetterAdsContentMode,
    private val adCache: AdResponseCache = AdResponseCache(),
    private val analyticsScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val logger = Logger.getLogger("com.betterads.BetterAdsClient")

    constructor(
        configuration: BetterAdsConfiguration,
        authProvider: BetterAdsAuthProviding? = null,
        httpClient: HttpClient? = null,
    ) : this(
        create(
            configuration = configuration,
            authProvider = authProvider,
            httpClient = httpClient ?: UrlConnectionHttpClient(),
        ),
    )

    private constructor(parts: Created) : this(
        api = parts.api,
        identity = parts.identity,
        contentProvider = parts.contentProvider,
        contentMode = parts.contentMode,
    )

    /**
     * Updates the account id sent on ads analytics.
     * Pass `null` on logout / guest. Clearing a previous non-nil user id rotates session
     * when the SDK owns `session_id`.
     */
    fun setUserId(userId: String?) {
        identity.setUserId(userId)
    }

    /** Last successfully fetched creative for [type], if any (process memory). */
    internal fun cachedAd(type: AdType): AdModel? = adCache.ad(type)

    suspend fun fetchAd(type: AdType): AdModel {
        val format = AdFormat.fromRaw(type.rawValue)
        val ad = if (format != null) {
            contentProvider.fetchAd(format)
        } else {
            api.fetchAd(type)
        }
        adCache.store(ad, type)
        return ad
    }

    suspend fun fetchAd(format: AdFormat): AdModel = fetchAd(AdType(format))

    fun trackImpression(adType: AdType) {
        if (contentMode == BetterAdsContentMode.FIXTURE) return
        analyticsScope.launch {
            runCatching { api.postImpression(adType) }
                .onFailure {
                    logger.log(Level.WARNING, "Impression tracking failed for ${adType.rawValue}", it)
                }
        }
    }

    fun trackClick(adType: AdType, ctaValue: String) {
        if (contentMode == BetterAdsContentMode.FIXTURE) return
        analyticsScope.launch {
            runCatching { api.postClick(adType, ctaValue) }
                .onFailure {
                    logger.log(Level.WARNING, "Click tracking failed for ${adType.rawValue}", it)
                }
        }
    }

    companion object {
        /** Spike-friendly client with built-in sample creatives (no base URL). */
        fun fixture(
            apiKey: String,
            userId: String? = null,
            locale: Locale = Locale.getDefault(),
        ): BetterAdsClient = BetterAdsClient(
            configuration = BetterAdsConfiguration.fixture(
                apiKey = apiKey,
                userId = userId,
                locale = locale,
            ),
        )

        private fun create(
            configuration: BetterAdsConfiguration,
            authProvider: BetterAdsAuthProviding?,
            httpClient: HttpClient,
        ): Created {
            val identity = BetterAdsIdentityStore(
                deviceId = configuration.deviceId,
                sessionId = configuration.sessionId,
                userId = configuration.userId,
            )
            val api = AdsApiClient(
                configuration = configuration,
                identity = identity,
                httpClient = httpClient,
                authProvider = authProvider,
            )
            val contentProvider: BetterAdsContentProviding = when (configuration.contentMode) {
                BetterAdsContentMode.FIXTURE -> FixtureBetterAdsContentProvider()
                BetterAdsContentMode.BOOKIE_GET_AD,
                BetterAdsContentMode.SERVE_V1,
                BetterAdsContentMode.DEDICATED_API,
                -> HttpBetterAdsContentProvider(api)
            }
            return Created(
                api = api,
                identity = identity,
                contentProvider = contentProvider,
                contentMode = configuration.contentMode,
            )
        }
    }

    private data class Created(
        val api: AdsApiClient,
        val identity: BetterAdsIdentityStore,
        val contentProvider: BetterAdsContentProviding,
        val contentMode: BetterAdsContentMode,
    )
}
