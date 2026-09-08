package com.betterads.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterads.model.AdFormat

object AdLayoutMetrics {
    val advertisementLabelInset: Dp = 8.dp
    val advertisementLabelCornerRadius: Dp = 4.dp
    const val advertisementLabelBackgroundOpacity = 0.72f
    val cornerRadius: Dp = 12.dp

    /** NativeOS Template 1x frames (logical dp). */
    fun templateSize(format: AdFormat): DpSize = when (format) {
        AdFormat.COMPACT -> DpSize(329.dp, 51.dp)
        AdFormat.BANNER -> DpSize(345.dp, 164.dp)
        AdFormat.CARD -> DpSize(336.dp, 443.dp)
            AdFormat.INTERSTITIAL -> DpSize(0.dp, 0.dp)
    }

    /** Keeps lazy host lists from skipping the slot before serve completes. */
    fun loadingPlaceholderMinHeight(format: AdFormat): Dp = templateSize(format).height

    fun advertisementLabelStyle(format: AdFormat): AdAdvertisementLabelStyle =
        if (format == AdFormat.CARD) AdAdvertisementLabelStyle.Full else AdAdvertisementLabelStyle.Short
}

object AdTypography {
    val caption10 = 10.sp
}
