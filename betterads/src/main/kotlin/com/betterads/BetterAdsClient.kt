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
 * Matches iOS `BetterAdsClient`.
 */
class BetterAdsClient internal constructor(
    private val api: AdsApiClient,
    private val contentProvider: BetterAdsContentProviding,
    private val contentMode: BetterAdsContentMode,
    private val analyticsScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val logger = Logger.getLogger("com.betterads.BetterAdsClient")

    constructor(
        configuration: BetterAdsConfiguration,
        authProvider: BetterAdsAuthProviding? = null,
        httpClient: HttpClient? = null,
    ) : this(
        api = AdsApiClient(
            configuration = configuration,
            httpClient = httpClient ?: UrlConnectionHttpClient(),
            authProvider = authProvider,
        ),
        contentProvider = when (configuration.contentMode) {
            BetterAdsContentMode.FIXTURE -> FixtureBetterAdsContentProvider()
            BetterAdsContentMode.BOOKIE_GET_AD,
            BetterAdsContentMode.DEDICATED_API,
            -> HttpBetterAdsContentProvider(
                AdsApiClient(
                    configuration = configuration,
                    httpClient = httpClient ?: UrlConnectionHttpClient(),
                    authProvider = authProvider,
                ),
            )
        },
        contentMode = configuration.contentMode,
    )

    suspend fun fetchAd(type: AdType): AdModel {
        val format = AdFormat.fromRaw(type.rawValue)
        return if (format != null) {
            contentProvider.fetchAd(format)
        } else {
            api.fetchAd(type)
        }
    }

    suspend fun fetchAd(format: AdFormat): AdModel = contentProvider.fetchAd(format)

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
            locale: Locale = Locale.getDefault(),
        ): BetterAdsClient = BetterAdsClient(
            configuration = BetterAdsConfiguration.fixture(apiKey = apiKey, locale = locale),
        )
    }
}
