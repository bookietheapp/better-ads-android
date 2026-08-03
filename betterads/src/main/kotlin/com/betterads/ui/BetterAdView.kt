package com.betterads.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.betterads.BetterAdsClient
import com.betterads.model.AdCtaAction
import com.betterads.model.AdFormat
import com.betterads.model.AdModel

/**
 * Ready-to-display ad view for Bookie-parity formats (`compact` / `banner` / `card`).
 *
 * Lifecycle (all owned by the SDK — hosts only place this composable):
 * - Revalidates with the serve API when the slot is in a STARTED lifecycle (first show
 *   and returning to the screen). No host refresh tokens.
 * - Keeps the current creative on screen while fetching (no flash).
 * - The API decides whether to return the same or a new creative; UI swaps only
 *   when the payload changes.
 *
 * The SDK fetches, renders, tracks impression/click, and opens CTA destinations.
 * Host callbacks are observation-only (e.g. Firebase bridge).
 */
val LocalBetterAdsClient = staticCompositionLocalOf<BetterAdsClient?> { null }

@Composable
fun ProvideBetterAdsClient(
    client: BetterAdsClient,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalBetterAdsClient provides client, content = content)
}

@Composable
fun BetterAdView(
    format: AdFormat,
    modifier: Modifier = Modifier,
    client: BetterAdsClient? = null,
    onImpression: ((AdModel) -> Unit)? = null,
    onClick: ((AdCtaAction) -> Unit)? = null,
) {
    val resolvedClient = client ?: LocalBetterAdsClient.current
    if (resolvedClient == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (format == AdFormat.BANNER) Modifier.height(AdLayoutMetrics.bannerHeight)
                    else Modifier,
                ),
        )
        return
    }

    BetterAdContent(
        client = resolvedClient,
        format = format,
        onImpression = onImpression,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun BetterAdContent(
    client: BetterAdsClient,
    format: AdFormat,
    onImpression: ((AdModel) -> Unit)?,
    onClick: ((AdCtaAction) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(client, format) {
        AdViewModel.forFormat(client, format)
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // SDK-owned revalidation — host apps never pass refresh epochs.
    LaunchedEffect(lifecycleOwner, viewModel, format) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.revalidate()
        }
    }

    when (val state = viewModel.state) {
        AdViewModel.State.Idle, AdViewModel.State.Loading -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .then(
                        if (format == AdFormat.BANNER) {
                            Modifier.height(AdLayoutMetrics.bannerHeight)
                        } else {
                            Modifier
                        },
                    ),
            )
        }

        is AdViewModel.State.Failed -> {
            // Match iOS: failed ads render nothing.
        }

        is AdViewModel.State.Loaded -> {
            LaunchedEffect(state.ad) {
                if (viewModel.trackImpressionIfNeeded()) {
                    onImpression?.invoke(state.ad)
                }
            }

            val handleCta = {
                val action = viewModel.handleClick()
                if (action != null) {
                    AdActionHandler.open(context, action)
                    onClick?.invoke(action)
                }
            }

            when (format) {
                AdFormat.COMPACT -> CompactAdLayout(ad = state.ad, onCta = handleCta, modifier = modifier)
                AdFormat.BANNER -> BannerAdLayout(ad = state.ad, onCta = handleCta, modifier = modifier)
                AdFormat.CARD -> CardAdLayout(ad = state.ad, onCta = handleCta, modifier = modifier)
                AdFormat.INTERSTITIAL -> Unit
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BetterAdViewPreview() {
    ProvideBetterAdsClient(BetterAdsClient.fixture(apiKey = "preview")) {
        BetterAdView(format = AdFormat.BANNER)
    }
}
