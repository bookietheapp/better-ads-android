package com.betterads

import com.betterads.model.AdFormat
import com.betterads.model.AdModel
import com.betterads.model.AdType
import com.betterads.model.BetterAdsError
import com.betterads.model.AdEvent
import com.betterads.model.AdEventType
import com.betterads.network.AdEventFlushCoordinator
import com.betterads.network.AdEventQueue
import com.betterads.network.AdsApiClient
import com.betterads.network.HttpClient
import com.betterads.network.InMemoryAdEventStore
import com.betterads.network.SharedPreferencesAdEventStore
import com.betterads.network.UrlConnectionHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
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
    private val configuration: BetterAdsConfiguration,
    private val api: AdsApiClient,
    private val identity: BetterAdsIdentityStore,
    private val eventQueue: AdEventQueue,
    private val flushCoordinator: AdEventFlushCoordinator?,
    private val contentProvider: BetterAdsContentProviding,
    private val contentMode: BetterAdsContentMode,
    private val adCache: AdResponseCache = AdResponseCache(),
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
            eventStore = null,
            flushScheduler = null,
        ),
    )

    internal constructor(
        configuration: BetterAdsConfiguration,
        authProvider: BetterAdsAuthProviding?,
        httpClient: HttpClient,
        eventStore: com.betterads.network.AdEventStore?,
        flushScheduler: com.betterads.network.AdEventFlushScheduler?,
    ) : this(
        create(
            configuration = configuration,
            authProvider = authProvider,
            httpClient = httpClient,
            eventStore = eventStore,
            flushScheduler = flushScheduler,
        ),
    )

    private constructor(parts: Created) : this(
        configuration = parts.configuration,
        api = parts.api,
        identity = parts.identity,
        eventQueue = parts.eventQueue,
        flushCoordinator = parts.flushCoordinator,
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

    fun trackImpression(campaignId: String) {
        if (contentMode == BetterAdsContentMode.FIXTURE) return
        val campaignIdInt = parseCampaignId(campaignId)
        if (campaignIdInt == null) {
            logger.warning("Skipping impression — invalid campaign_id: $campaignId")
            return
        }
        val id = identity.snapshot()
        eventQueue.enqueue(
            AdEvent(
                type = AdEventType.IMPRESSION,
                campaignId = campaignIdInt,
                deviceId = id.deviceId,
                sessionId = id.sessionId,
                userId = id.userId,
                locale = configuration.locale.toLanguageTag(),
            ),
        )
    }

    fun trackClick(campaignId: String, ctaValue: String) {
        if (contentMode == BetterAdsContentMode.FIXTURE) return
        val campaignIdInt = parseCampaignId(campaignId)
        if (campaignIdInt == null) {
            logger.warning("Skipping click — invalid campaign_id: $campaignId")
            return
        }
        val id = identity.snapshot()
        eventQueue.enqueue(
            AdEvent(
                type = AdEventType.CLICK,
                campaignId = campaignIdInt,
                deviceId = id.deviceId,
                sessionId = id.sessionId,
                userId = id.userId,
                locale = configuration.locale.toLanguageTag(),
                ctaValue = ctaValue,
            ),
        )
    }

    private fun parseCampaignId(raw: String): Int? {
        val value = raw.trim().toIntOrNull() ?: return null
        return value.takeIf { it > 0 }
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
            eventStore: com.betterads.network.AdEventStore?,
            flushScheduler: com.betterads.network.AdEventFlushScheduler?,
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
            val resolvedEventStore = eventStore
                ?: BetterAds.appContext?.let { SharedPreferencesAdEventStore(it) }
                ?: InMemoryAdEventStore()
            val eventQueue = AdEventQueue(
                store = resolvedEventStore,
                postEvents = { events -> api.postEvents(events) },
                flushScheduler = flushScheduler ?: { operation ->
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { operation() }
                    Unit
                },
            )
            val flushCoordinator = if (configuration.contentMode != BetterAdsContentMode.FIXTURE) {
                AdEventFlushCoordinator(
                    queue = eventQueue,
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                ).also { it.start() }
            } else {
                null
            }
            val contentProvider: BetterAdsContentProviding = when (configuration.contentMode) {
                BetterAdsContentMode.FIXTURE -> FixtureBetterAdsContentProvider()
                BetterAdsContentMode.BOOKIE_GET_AD,
                BetterAdsContentMode.SERVE_V1,
                BetterAdsContentMode.DEDICATED_API,
                -> HttpBetterAdsContentProvider(api)
            }
            return Created(
                configuration = configuration,
                api = api,
                identity = identity,
                eventQueue = eventQueue,
                flushCoordinator = flushCoordinator,
                contentProvider = contentProvider,
                contentMode = configuration.contentMode,
            )
        }
    }

    private data class Created(
        val configuration: BetterAdsConfiguration,
        val api: AdsApiClient,
        val identity: BetterAdsIdentityStore,
        val eventQueue: AdEventQueue,
        val flushCoordinator: AdEventFlushCoordinator?,
        val contentProvider: BetterAdsContentProviding,
        val contentMode: BetterAdsContentMode,
    )
}
