package com.betterads.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterads.model.AdFormat

object AdLayoutMetrics {
    val advertisementLabelInset: Dp = 8.dp
    val advertisementLabelCornerRadius: Dp = 4.dp
    val advertisementLabelBackgroundOpacity = 0.72f
    val cornerRadius: Dp = 12.dp
    const val skeletonFillOpacity = 0.10f
    const val skeletonPulseOpacity = 0.55f
    const val skeletonPulseDurationMs = 900

    /**
     * NativeOS Template 1x frames (logical dp), authored against a 390 dp screen.
     *
     * These are reference sizes, not fixed frames — the rendered slot stretches to the
     * host's content width at [templateAspectRatio] so it lines up with the surrounding
     * layout on every device.
     */
    fun templateSize(format: AdFormat): DpSize = when (format) {
        AdFormat.COMPACT -> DpSize(329.dp, 51.dp)
        AdFormat.BANNER -> DpSize(345.dp, 164.dp)
        AdFormat.CARD -> DpSize(336.dp, 443.dp)
        AdFormat.INTERSTITIAL -> DpSize(0.dp, 0.dp)
    }

    /**
     * Width-over-height ratio of the Template frame. Drives the rendered slot so a wider
     * phone gets a wider ad instead of a centered 390 dp-era box.
     */
    fun templateAspectRatio(format: AdFormat): Float {
        val size = templateSize(format)
        if (size.height.value <= 0f) return 1f
        return size.width.value / size.height.value
    }

    /** Template-sized skeleton while Serve is in flight so the slot stays visible. */
    fun loadingPlaceholderMinHeight(format: AdFormat): Dp = templateSize(format).height

    fun advertisementLabelStyle(format: AdFormat): AdAdvertisementLabelStyle =
        if (format == AdFormat.CARD) AdAdvertisementLabelStyle.Full else AdAdvertisementLabelStyle.Short
}

object AdTypography {
    val caption10 = 10.sp
}
