package com.betterads.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.betterads.BetterAdsClient
import com.betterads.model.AdCtaAction
import com.betterads.model.AdFormat
import com.betterads.model.AdModel

/**
 * Ready-to-display ad view for Bookie-parity formats (`compact` / `banner` / `card`).
 *
 * Lifecycle (all owned by the SDK — hosts only place this composable):
 * - Loads from Serve on first display (or from the in-memory cache on remount).
 * - Revalidates only when the host screen session changes (pull-to-refresh / new visit).
 * - Scrolling the slot off and back does **not** refetch or show the skeleton again.
 * - Keeps the current creative on screen while a session revalidate is in flight.
 * - The API decides whether to return the same or a new creative; UI swaps only
 *   when the payload changes.
 * - Pass [externalAdId] for keyed Serve. A keyed miss renders nothing and reports
 *   `onAvailabilityChanged(false)` — it does **not** fall back to unkeyed Serve.
 *
 * The SDK fetches, renders the hero image, tracks impression/click, and opens `ctaLink`.
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
    externalAdId: String? = null,
    onImpression: ((AdModel) -> Unit)? = null,
    onClick: ((AdCtaAction) -> Unit)? = null,
    /** `true` when a creative is shown; `false` when serve failed with no cached creative. */
    onAvailabilityChanged: ((Boolean) -> Unit)? = null,
    /**
     * Host screen-session token. Optional.
     *
     * The SDK already counts at most one impression per placement + ad id across
     * list recycle. Changing this value on a **still-composed** view re-arms
     * that placement (pull-to-refresh / new visit while the row is on screen).
     * A new UUID created inside a lazy item is ignored for counting.
     *
     * To re-arm after rows were disposed, call [BetterAdsClient.resetImpressionSession]
     * from the screen visit hook instead of minting per-row ids.
     */
    impressionSessionId: String? = null,
) {
    val resolvedClient = client ?: LocalBetterAdsClient.current
    if (resolvedClient == null) {
        return
    }

    BetterAdContent(
        client = resolvedClient,
        format = format,
        externalAdId = externalAdId,
        onImpression = onImpression,
        onClick = onClick,
        onAvailabilityChanged = onAvailabilityChanged,
        impressionSessionId = impressionSessionId,
        modifier = modifier,
    )
}

@Composable
private fun BetterAdContent(
    client: BetterAdsClient,
    format: AdFormat,
    externalAdId: String?,
    onImpression: ((AdModel) -> Unit)?,
    onClick: ((AdCtaAction) -> Unit)?,
    onAvailabilityChanged: ((Boolean) -> Unit)?,
    impressionSessionId: String?,
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(client, format, externalAdId) {
        AdViewModel.forFormat(client, format, externalAdId)
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastImpressionSessionId by remember { mutableStateOf(impressionSessionId) }

    // First display only. Lazy lists dispose this composable when the row leaves
    // the window — `loadIfNeeded` must not refetch a cached creative.
    LaunchedEffect(client, format, externalAdId) {
        viewModel.loadIfNeeded()
    }

    LaunchedEffect(impressionSessionId) {
        if (lastImpressionSessionId != impressionSessionId) {
            viewModel.resetImpressionEligibility()
            viewModel.revalidate()
            lastImpressionSessionId = impressionSessionId
        }
    }

    LaunchedEffect(viewModel.state, lifecycleOwner) {
        if (viewModel.state is AdViewModel.State.Idle &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            viewModel.loadIfNeeded()
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START &&
                viewModel.state is AdViewModel.State.Idle
            ) {
                lifecycleOwner.lifecycleScope.launch {
                    viewModel.loadIfNeeded()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel.state) {
        when (viewModel.state) {
            is AdViewModel.State.Loaded -> onAvailabilityChanged?.invoke(true)
            is AdViewModel.State.Failed -> onAvailabilityChanged?.invoke(false)
            AdViewModel.State.Idle, AdViewModel.State.Loading -> Unit
        }
    }

    when (val state = viewModel.state) {
        AdViewModel.State.Idle, AdViewModel.State.Loading -> {
            AdSkeleton(format = format, modifier = modifier)
        }

        is AdViewModel.State.Failed -> {
            // No inventory / network error — render nothing.
        }

        is AdViewModel.State.Loaded -> {
            val handleCta = {
                val action = viewModel.handleClick()
                if (action != null) {
                    AdActionHandler.open(context, action)
                    onClick?.invoke(action)
                }
            }

            when (format) {
                AdFormat.COMPACT, AdFormat.BANNER, AdFormat.CARD ->
                    HeroAdLayout(
                        ad = state.ad,
                        format = format,
                        onCta = handleCta,
                        modifier = modifier.reportWhenAdVisible(
                            trackingKey = "${impressionSessionId.orEmpty()}|${state.ad.adId}",
                        ) {
                            if (viewModel.trackImpressionIfNeeded(impressionSessionId)) {
                                onImpression?.invoke(state.ad)
                            }
                        },
                    )
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

@Preview(showBackground = true)
@Composable
private fun AdSkeletonPreview() {
    AdSkeleton(format = AdFormat.BANNER)
}
