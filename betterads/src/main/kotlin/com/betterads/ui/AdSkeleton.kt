package com.betterads.ui

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.betterads.R
import com.betterads.model.AdFormat

/**
 * Template-shaped placeholder shown while Serve is in flight.
 *
 * Matches the loaded hero frame (width, aspect ratio, corner radius, Ad chip) so the
 * slot does not collapse, flash empty, or resize when the creative arrives. Motion is
 * a gentle pulse.
 */
@Composable
fun AdSkeleton(
    format: AdFormat,
    modifier: Modifier = Modifier,
) {
    val loading = stringResource(R.string.better_ads_loading_advertisement)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(AdLayoutMetrics.templateAspectRatio(format))
            .clip(RoundedCornerShape(AdLayoutMetrics.cornerRadius))
            .semantics { contentDescription = loading },
    ) {
        AdSkeletonFill()
        AdAdvertisementLabel(
            style = AdLayoutMetrics.advertisementLabelStyle(format),
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

/** Pulsing fill used by [AdSkeleton] and as the hero-image load placeholder. */
@Composable
fun AdSkeletonFill(modifier: Modifier = Modifier) {
    val fillColor = if (isSystemInDarkTheme()) {
        Color.White.copy(alpha = AdLayoutMetrics.skeletonFillOpacity)
    } else {
        Color.Black.copy(alpha = AdLayoutMetrics.skeletonFillOpacity)
    }
    val fillModifier = modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(AdLayoutMetrics.cornerRadius))

    if (systemAnimationsEnabled()) {
        PulsingSkeletonFill(fillColor = fillColor, modifier = fillModifier)
    } else {
        Box(modifier = fillModifier.background(fillColor))
    }
}

@Composable
private fun PulsingSkeletonFill(fillColor: Color, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "ad-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = AdLayoutMetrics.skeletonPulseOpacity,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AdLayoutMetrics.skeletonPulseDurationMs,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ad-skeleton-pulse",
    )
    Box(modifier = modifier.background(fillColor.copy(alpha = fillColor.alpha * alpha)))
}

@Composable
private fun systemAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
}

@Preview(showBackground = true)
@Composable
private fun AdSkeletonBannerPreview() {
    AdSkeleton(format = AdFormat.BANNER)
}

@Preview(showBackground = true)
@Composable
private fun AdSkeletonCompactPreview() {
    AdSkeleton(format = AdFormat.COMPACT)
}

@Preview(showBackground = true)
@Composable
private fun AdSkeletonCardPreview() {
    AdSkeleton(format = AdFormat.CARD)
}
