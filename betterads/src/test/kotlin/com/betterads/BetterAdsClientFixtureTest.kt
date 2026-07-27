package com.betterads

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.betterads.model.AdFormat
import com.betterads.model.AdType
import com.betterads.model.BetterAdsError
import com.betterads.ui.AdFormatting
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        client.trackImpression(AdType(AdFormat.BANNER))
        client.trackClick(AdType(AdFormat.BANNER), "https://example.com")
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
