package com.betterads

import com.betterads.model.AdEvent
import com.betterads.model.AdEventType
import com.betterads.model.AdFormat
import com.betterads.model.AdType
import com.betterads.model.BetterAdsError
import com.betterads.ui.AdViewModel
import com.betterads.network.HttpClient
import com.betterads.network.HttpRequest
import com.betterads.network.HttpResponse
import com.betterads.network.InMemoryAdEventStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BetterAdsClientFixtureTest {
    @Test
    fun fixture_returnsSampleCreativeForKnownFormats() = runTest {
        val client = BetterAdsClient.fixture(apiKey = "ba_test_key")
        val ad = client.fetchAd(AdFormat.BANNER)
        assertEquals("banner", ad.size)
        assertEquals("1", ad.adId)
        assertTrue(ad.ctaLink.isNotBlank())
    }

    @Test
    fun fixture_rejectsInterstitial() = runTest {
        val client = BetterAdsClient.fixture(apiKey = "ba_test_key")
        try {
            client.fetchAd(AdFormat.INTERSTITIAL)
            error("expected failure")
        } catch (e: BetterAdsError.UnknownAdType) {
            assertEquals("interstitial", e.type.rawValue)
        }
    }

    @Test
    fun fixture_skipsAnalyticsWithoutThrowing() {
        val client = BetterAdsClient.fixture(apiKey = "ba_test_key")
        client.trackImpression("42")
        client.trackClick("42", "https://example.com")
    }
}

class BetterAdsClientServeV1Test {
    private val sampleAdJson = """
        {
          "adId": "42",
          "campaignId": "10",
          "size": "banner",
          "images": {
            "hero": {
              "1x": "https://cdn.example.com/hero.png",
              "2x": "https://cdn.example.com/diana.k@example.org",
              "3x": "https://cdn.example.com/james.b@example.com"
            }
          },
          "ctaLink": "https://example.com/offer"
        }
    """.trimIndent()

    @Test
    fun serveV1_includesAppAndSizeQuery() = runTest {
        val http = RecordingHttpClient(HttpResponse(200, sampleAdJson.toByteArray()))
        val client = BetterAdsClient(
            configuration = BetterAdsConfiguration(
                apiKey = "",
                contentMode = BetterAdsContentMode.SERVE_V1,
                appName = "Bookie",
            ),
            httpClient = http,
        )

        client.fetchAd(AdFormat.BANNER)

        assertEquals(1, http.requests.size)
        assertEquals(
            "${BetterAdsEndpoints.SERVE_V1_BASE_URL}/api/v1/serve?app=Bookie&size=banner",
            http.requests[0].url,
        )
        assertNull(http.requests[0].headers["X-Api-Key"])
    }

    @Test
    fun serveV1_omitsAppWhenAppNameNull_sendsApiKey() = runTest {
        val http = RecordingHttpClient(HttpResponse(200, sampleAdJson.toByteArray()))
        val client = BetterAdsClient(
            configuration = BetterAdsConfiguration(
                apiKey = "future-key",
                contentMode = BetterAdsContentMode.SERVE_V1,
                appName = null,
            ),
            httpClient = http,
        )

        val ad = client.fetchAd(AdFormat.BANNER)
        assertEquals("42", ad.adId)
        assertEquals("10", ad.campaignId)
        assertEquals("https://example.com/offer", ad.ctaLink)

        assertEquals(
            "${BetterAdsEndpoints.SERVE_V1_BASE_URL}/api/v1/serve?size=banner",
            http.requests[0].url,
        )
        assertEquals("future-key", http.requests[0].headers["X-Api-Key"])
    }

    @Test
    fun serveV1_includesExternalAdIdWhenKeyed() = runTest {
        val http = RecordingHttpClient(HttpResponse(200, sampleAdJson.toByteArray()))
        val client = BetterAdsClient(
            configuration = BetterAdsConfiguration(
                apiKey = "nos_test",
                contentMode = BetterAdsContentMode.SERVE_V1,
                appName = "Bookie",
            ),
            httpClient = http,
        )

        client.fetchAd(AdFormat.BANNER, externalAdId = "book_of_the_week_de")

        assertEquals(1, http.requests.size)
        assertEquals(
            "${BetterAdsEndpoints.SERVE_V1_BASE_URL}/api/v1/serve?app=Bookie&size=banner&externalAdId=book_of_the_week_de",
            http.requests[0].url,
        )
    }

    @Test
    fun requestAd_isAliasForKeyedFetchAd() = runTest {
        val http = RecordingHttpClient(HttpResponse(200, sampleAdJson.toByteArray()))
        val client = BetterAdsClient(
            configuration = BetterAdsConfiguration(
                apiKey = "nos_test",
                contentMode = BetterAdsContentMode.SERVE_V1,
                appName = "Bookie",
            ),
            httpClient = http,
        )

        val ad = client.requestAd(AdFormat.BANNER, externalAdId = "book_of_the_week_de")
        assertEquals("42", ad.adId)
        assertTrue(http.requests[0].url.contains("externalAdId=book_of_the_week_de"))
    }

    @Test
    fun serveV1_omitsBlankExternalAdId() = runTest {
        val http = RecordingHttpClient(HttpResponse(200, sampleAdJson.toByteArray()))
        val client = BetterAdsClient(
            configuration = BetterAdsConfiguration(
                apiKey = "nos_test",
                contentMode = BetterAdsContentMode.SERVE_V1,
                appName = "Bookie",
            ),
            httpClient = http,
        )

        client.fetchAd(AdFormat.BANNER, externalAdId = "  ")

        assertEquals(
            "${BetterAdsEndpoints.SERVE_V1_BASE_URL}/api/v1/serve?app=Bookie&size=banner",
            http.requests[0].url,
        )
    }

    @Test
    fun keyed404_doesNotRetryUnkeyed() = runTest {
        val http = RecordingHttpClient(HttpResponse(404, """{"error":"not_found"}""".toByteArray()))
        val client = BetterAdsClient(
            configuration = BetterAdsConfiguration(
                apiKey = "nos_test",
                contentMode = BetterAdsContentMode.SERVE_V1,
                appName = "Bookie",
            ),
            httpClient = http,
        )

        try {
            client.fetchAd(AdFormat.BANNER, externalAdId = "book_of_the_week_de")
            error("expected keyed miss")
        } catch (e: BetterAdsError.UnknownAdType) {
            assertEquals("banner", e.type.rawValue)
        }

        assertEquals(1, http.requests.size)
        assertTrue(http.requests[0].url.contains("externalAdId=book_of_the_week_de"))
    }

    @Test
    fun keyedAndUnkeyedUseSeparateCacheSlots() = runTest {
        val http = RecordingHttpClient(
            HttpResponse(200, sampleAdJson.toByteArray()),
            HttpResponse(200, sampleAdJson.toByteArray()),
            HttpResponse(404, """{"error":"not_found"}""".toByteArray()),
        )
        val client = BetterAdsClient(
            configuration = BetterAdsConfiguration(
                apiKey = "nos_test",
                contentMode = BetterAdsContentMode.SERVE_V1,
                appName = "Bookie",
            ),
            httpClient = http,
        )

        client.fetchAd(AdFormat.BANNER)
        client.fetchAd(AdFormat.BANNER, externalAdId = "book_of_the_week_de")
        assertTrue(client.cachedAd(AdType(AdFormat.BANNER)) != null)
        assertTrue(client.cachedAd(AdType(AdFormat.BANNER), "book_of_the_week_de") != null)

        try {
            client.fetchAd(AdFormat.BANNER, externalAdId = "book_of_the_week_de")
            error("expected keyed miss")
        } catch (_: BetterAdsError.UnknownAdType) {
            // expected
        }

        assertTrue(client.cachedAd(AdType(AdFormat.BANNER)) != null)
        assertNull(client.cachedAd(AdType(AdFormat.BANNER), "book_of_the_week_de"))
    }

    @Test
    fun keyedMiss_failsEvenWhenPreviousCreativeWasShowing() = runTest {
        val http = RecordingHttpClient(
            HttpResponse(200, sampleAdJson.toByteArray()),
            HttpResponse(404, """{"error":"not_found"}""".toByteArray()),
        )
        val client = BetterAdsClient(
            configuration = BetterAdsConfiguration(
                apiKey = "nos_test",
                contentMode = BetterAdsContentMode.SERVE_V1,
                appName = "Bookie",
            ),
            httpClient = http,
        )
        val viewModel = AdViewModel(
            client = client,
            type = AdType(AdFormat.BANNER),
            externalAdId = "book_of_the_week_de",
        )

        viewModel.loadIfNeeded()
        assertTrue(viewModel.state is AdViewModel.State.Loaded)

        viewModel.revalidate()
        assertTrue(viewModel.state is AdViewModel.State.Failed)
        assertEquals(2, http.requests.size)
        assertTrue(http.requests.all { it.url.contains("externalAdId=book_of_the_week_de") })
    }

    @Test
    fun serveV1_includesIsTestEnvWhenEnabled() = runTest {
        val http = RecordingHttpClient(HttpResponse(200, sampleAdJson.toByteArray()))
        val client = BetterAdsClient(
            configuration = BetterAdsConfiguration(
                apiKey = "nos_test",
                contentMode = BetterAdsContentMode.SERVE_V1,
                appName = "Bookie",
                isTestEnv = true,
            ),
            httpClient = http,
        )

        client.fetchAd(AdFormat.BANNER)

        assertEquals(
            "${BetterAdsEndpoints.SERVE_V1_BASE_URL}/api/v1/serve?app=Bookie&size=banner&isTestEnv=true",
            http.requests[0].url,
        )
    }

    @Test
    fun serveV1_ignoresHostBaseUrl() = runTest {
        val http = RecordingHttpClient(HttpResponse(200, sampleAdJson.toByteArray()))
        val client = BetterAdsClient(
            configuration = BetterAdsConfiguration(
                apiKey = "",
                contentMode = BetterAdsContentMode.SERVE_V1,
                baseUrl = "https://ads.example.com",
                appName = "Bookie",
            ),
            httpClient = http,
        )

        client.fetchAd(AdFormat.BANNER)

        assertTrue(http.requests[0].url.startsWith(BetterAdsEndpoints.SERVE_V1_BASE_URL))
        assertFalse(http.requests[0].url.contains("ads.example.com"))
    }

    private class RecordingHttpClient(
        private vararg val responses: HttpResponse,
    ) : HttpClient {
        val requests = mutableListOf<HttpRequest>()
        private var index = 0

        override suspend fun send(request: HttpRequest): HttpResponse {
            requests += request
            return responses.getOrElse(index++) {
                error("RecordingHttpClient has no enqueued responses")
            }
        }
    }
}

class BetterAdsClientEventsTest {
    private val eventsSuccessJson = """{"ok":true,"accepted":1,"rejected":[]}"""

    @Test
    fun trackImpression_postsBatchedEventPayload() = runTest {
        val http = RecordingHttpClient(HttpResponse(200, eventsSuccessJson.toByteArray()))
        val store = InMemoryAdEventStore()
        val client = BetterAdsClient(
            configuration = BetterAdsConfiguration(
                apiKey = "test-key",
                contentMode = BetterAdsContentMode.BOOKIE_GET_AD,
                baseUrl = "https://ads.example.com",
                sessionId = "session-123",
                userId = "user-456",
                deviceId = "device-789",
            ),
            authProvider = null,
            httpClient = http,
            eventStore = store,
            flushScheduler = { operation -> kotlinx.coroutines.runBlocking { operation() } },
        )

        client.trackImpression("42")

        assertEquals(1, http.requests.size)
        assertEquals("POST", http.requests[0].method)
        assertEquals(
            "https://ads.example.com/api/v1/events",
            http.requests[0].url,
        )
        assertEquals("test-key", http.requests[0].headers["X-Api-Key"])

        val body = http.requests[0].body?.decodeToString().orEmpty()
        val event = Json.parseToJsonElement(body).jsonObject["events"]!!.jsonArray.single().jsonObject
        assertEquals("impression", event["type"]!!.jsonPrimitive.content)
        assertEquals("42", event["ad_id"]!!.jsonPrimitive.content)
        assertEquals("device-789", event["device_id"]!!.jsonPrimitive.content)
        assertEquals("session-123", event["session_id"]!!.jsonPrimitive.content)
        assertEquals("user-456", event["user_id"]!!.jsonPrimitive.content)
        assertEquals(BetterAdsSDK.VERSION, event["sdk_version"]!!.jsonPrimitive.content)
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun trackImpression_keepsEventsOnTransientFailure() = runTest {
        val http = RecordingHttpClient(HttpResponse(503, """{"error":"unavailable"}""".toByteArray()))
        val store = InMemoryAdEventStore()
        val client = BetterAdsClient(
            configuration = BetterAdsConfiguration(
                apiKey = "test-key",
                contentMode = BetterAdsContentMode.BOOKIE_GET_AD,
                baseUrl = "https://ads.example.com",
                sessionId = "session-123",
                deviceId = "device-789",
            ),
            authProvider = null,
            httpClient = http,
            eventStore = store,
            flushScheduler = { operation -> kotlinx.coroutines.runBlocking { operation() } },
        )

        client.trackImpression("42")

        assertEquals(1, http.requests.size)
        assertEquals(1, store.load().size)
    }

    @Test
    fun trackImpression_skipsInvalidAdId() = runTest {
        val http = RecordingHttpClient(HttpResponse(200, eventsSuccessJson.toByteArray()))
        val client = BetterAdsClient(
            configuration = BetterAdsConfiguration(
                apiKey = "test-key",
                contentMode = BetterAdsContentMode.BOOKIE_GET_AD,
                baseUrl = "https://ads.example.com",
            ),
            authProvider = null,
            httpClient = http,
            eventStore = null,
            flushScheduler = { operation -> kotlinx.coroutines.runBlocking { operation() } },
        )

        client.trackImpression("sample-campaign-01")

        assertTrue(http.requests.isEmpty())
    }

    private class RecordingHttpClient(
        private vararg val responses: HttpResponse,
    ) : HttpClient {
        val requests = mutableListOf<HttpRequest>()
        private var index = 0

        override suspend fun send(request: HttpRequest): HttpResponse {
            requests += request
            return responses.getOrElse(index++) {
                error("RecordingHttpClient has no enqueued responses")
            }
        }
    }
}

class AdEventTest {
    private val json = Json { encodeDefaults = false }

    @Test
    fun loggedIn_includesDeviceUserAndSession() {
        val encoded = json.encodeToString(
            AdEvent.serializer(),
            AdEvent(
                type = AdEventType.IMPRESSION,
                adId = 42,
                deviceId = "device-789",
                sessionId = "session-123",
                userId = "user-456",
                locale = "en-US",
            ),
        )
        val obj = json.parseToJsonElement(encoded).jsonObject
        assertEquals("device-789", obj["device_id"]!!.jsonPrimitive.content)
        assertEquals("session-123", obj["session_id"]!!.jsonPrimitive.content)
        assertEquals("user-456", obj["user_id"]!!.jsonPrimitive.content)
        assertEquals("42", obj["ad_id"]!!.jsonPrimitive.content)
        assertFalse(obj.containsKey("cta_value"))
    }

    @Test
    fun loggedOut_omitsUserIdKeepsDeviceId() {
        val encoded = json.encodeToString(
            AdEvent.serializer(),
            AdEvent(
                type = AdEventType.CLICK,
                adId = 42,
                deviceId = "device-789",
                sessionId = "session-123",
                userId = null,
                locale = "en-US",
                ctaValue = "https://example.com",
            ),
        )
        val obj = json.parseToJsonElement(encoded).jsonObject
        assertEquals("device-789", obj["device_id"]!!.jsonPrimitive.content)
        assertFalse(obj.containsKey("user_id"))
        assertEquals("https://example.com", obj["cta_value"]!!.jsonPrimitive.content)
    }
}
