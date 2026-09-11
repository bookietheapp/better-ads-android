package com.betterads.ui

import com.betterads.model.AdFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class AdFormattingTest {
    @Test
    fun aspectRatioMatchesTemplateFrame() {
        listOf(AdFormat.COMPACT, AdFormat.BANNER, AdFormat.CARD).forEach { format ->
            val size = AdLayoutMetrics.templateSize(format)
            assertEquals(
                "$format ratio must track its template frame",
                size.width.value / size.height.value,
                AdLayoutMetrics.templateAspectRatio(format),
                0.0001f,
            )
        }
    }

    @Test
    fun aspectRatioScalesTemplateWidthToWiderScreens() {
        // 430 dp screen with Bookie's 16 dp gutters.
        val contentWidth = 398f
        val height = contentWidth / AdLayoutMetrics.templateAspectRatio(AdFormat.BANNER)
        assertEquals(189.2f, height, 0.1f)
    }

    @Test
    fun interstitialHasNoDegenerateRatio() {
        assertEquals(1f, AdLayoutMetrics.templateAspectRatio(AdFormat.INTERSTITIAL), 0.0001f)
    }
}
