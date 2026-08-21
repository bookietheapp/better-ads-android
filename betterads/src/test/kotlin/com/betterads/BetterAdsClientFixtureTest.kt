package com.betterads

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.betterads.model.AdEvent
import com.betterads.model.AdEventType
import com.betterads.model.AdFormat
import com.betterads.model.AdType
import com.betterads.model.BetterAdsError
import com.betterads.network.HttpClient
import com.betterads.network.HttpRequest
import com.betterads.network.HttpResponse
import com.betterads.network.InMemoryAdEventStore
import com.betterads.ui.AdFormatting
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
        assertEquals("Sample Brand", ad.brand)
        assertTrue(ad.cta.action.value.isNotBlank())
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
          "campaignId": "42",
          "size": "banner",
          "brand": "Sample Brand",
          "backgroundColor": "#CC96FF",
          "textColor": "#000000",
          "headline": "Sample Brand",
          "description": "60 days free",
          "images": {
            "hero": { "1x": "https://cdn.example.com/hero.png", "2x": "", "3x": "" },
            "icon": { "1x": "https://cdn.example.com/icon.png", "2x": "", "3x": "" }
          },
          "cta": {
            "title": "Learn more",
            "ctaButtonColor": "#FFFFFF",
            "ctaTitleColor": "#000000",
            "action": { "type": "url", "value": "https://example.com/offer" }
          }
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
        assertNull(http.requests[0].headers["X-API-Key"])
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

        client.fetchAd(AdFormat.BANNER)

        assertEquals(
            "${BetterAdsEndpoints.SERVE_V1_BASE_URL}/api/v1/serve?size=banner",
            http.requests[0].url,
        )
        assertEquals("future-key", http.requests[0].headers["X-API-Key"])
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
        assertEquals("test-key", http.requests[0].headers["X-API-Key"])

        val body = http.requests[0].body?.decodeToString().orEmpty()
        val event = Json.parseToJsonElement(body).jsonObject["events"]!!.jsonArray.single().jsonObject
        assertEquals("impression", event["type"]!!.jsonPrimitive.content)
        assertEquals("42", event["campaign_id"]!!.jsonPrimitive.content)
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
    fun trackImpression_skipsInvalidCampaignId() = runTest {
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

class AdFormattingTest {
    @Test
    fun annotatedDescription_parsesEmphasisMarkers() {
        val text = AdFormatting.annotatedDescription(
            text = "60 days *free* today",
            baseColor = Color.Black,
            baseFontSize = 14.sp,
        )
        assertEquals("60 days free today", text.text)
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
                campaignId = 42,
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
        assertEquals("42", obj["campaign_id"]!!.jsonPrimitive.content)
        assertFalse(obj.containsKey("cta_value"))
    }

    @Test
    fun loggedOut_omitsUserIdKeepsDeviceId() {
        val encoded = json.encodeToString(
            AdEvent.serializer(),
            AdEvent(
                type = AdEventType.CLICK,
                campaignId = 42,
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
