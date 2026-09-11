package com.betterads.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.betterads.AdLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.min

/** Bookie impression thresholds (book covers + placement ads). */
internal object AdImpressionPolicy {
    const val MIN_VISIBLE_FRACTION = 0.5f
    const val MIN_ACCUMULATED_VISIBLE_MS = 200.0
    const val MAX_SCROLL_VELOCITY_POINTS_PER_SECOND = 1_200f
    const val VELOCITY_WARMUP_SECONDS = 0.35
    const val SAMPLE_PERIOD_MS = 33L
}

/**
 * Accumulates Bookie-parity dwell / velocity until one qualification, then latches.
 * [nowSeconds] must be a monotonic clock (e.g. elapsed realtime seconds).
 * Positions should be in density-independent points.
 *
 * Velocity is **vertical** (center Y), matching Bookie's Android book-cover tracker
 * and the impression spec — the tracked target is the whole ad view, not a cover.
 */
internal class AdImpressionVisibilityTracker {
    var hasQualified: Boolean = false
        private set

    private var accumulatedEligibleMs = 0.0
    private var lastTickMonotonic: Double? = null
    private var lastCenterY: Float? = null
    private var lastFrameMonotonic: Double? = null
    private var velocityWarmupUntil: Double? = null

    fun reset() {
        hasQualified = false
        accumulatedEligibleMs = 0.0
        lastTickMonotonic = null
        lastCenterY = null
        lastFrameMonotonic = null
        velocityWarmupUntil = null
    }

    /** @return true once, when the creative has been eligible long enough. */
    fun update(
        visibleFraction: Float,
        centerY: Float,
        width: Float,
        height: Float,
        nowSeconds: Double,
        appActive: Boolean,
    ): Boolean {
        if (hasQualified) return false

        if (visibleFraction < AdImpressionPolicy.MIN_VISIBLE_FRACTION) {
            accumulatedEligibleMs = 0.0
            lastTickMonotonic = nowSeconds
            lastCenterY = null
            lastFrameMonotonic = null
            return false
        }

        val lastTick = lastTickMonotonic
        val deltaTime = if (lastTick != null) {
            min(maxOf(nowSeconds - lastTick, 0.0), 0.25)
        } else {
            1.0 / 60.0
        }
        lastTickMonotonic = nowSeconds

        val velocity = estimatedVerticalVelocity(centerY, nowSeconds)
        if (velocityWarmupUntil == null && width > 1f && height > 1f) {
            velocityWarmupUntil = nowSeconds + AdImpressionPolicy.VELOCITY_WARMUP_SECONDS
        }
        val withinWarmup = velocityWarmupUntil?.let { nowSeconds < it } ?: false
        val velocityOk = velocity <= AdImpressionPolicy.MAX_SCROLL_VELOCITY_POINTS_PER_SECOND
        val allowsAccumulation = appActive && !withinWarmup && velocityOk
        if (allowsAccumulation) {
            accumulatedEligibleMs += deltaTime * 1_000
        }

        lastCenterY = centerY
        lastFrameMonotonic = nowSeconds

        if (accumulatedEligibleMs < AdImpressionPolicy.MIN_ACCUMULATED_VISIBLE_MS) return false
        hasQualified = true
        return true
    }

    private fun estimatedVerticalVelocity(centerY: Float, nowSeconds: Double): Float {
        val previousTime = lastFrameMonotonic ?: return 0f
        val previousY = lastCenterY ?: return 0f
        val dt = nowSeconds - previousTime
        if (dt <= 1e-6) return 0f
        return abs(centerY - previousY) / dt.toFloat()
    }
}

/**
 * Bookie geometry applied to the **whole ad view** (not a book-cover subrect):
 * `visible = area(clipped ∩ viewport) / area(unclipped)`.
 *
 * Viewport is the window inset by system bars (safe drawing). Collapsed slivers
 * under [MIN_FRAME_PX] are treated as not viewable.
 */
internal object AdVisibility {
    const val MIN_VISIBLE_FRACTION = AdImpressionPolicy.MIN_VISIBLE_FRACTION
    const val MIN_FRAME_PX = 8f

    fun impressionViewportRect(
        screenWidthPx: Float,
        screenHeightPx: Float,
        insetLeft: Float,
        insetTop: Float,
        insetRight: Float,
        insetBottom: Float,
    ): Rect {
        val left = insetLeft
        val top = insetTop
        val right = screenWidthPx - insetRight
        val bottom = screenHeightPx - insetBottom
        if (right <= left || bottom <= top) return Rect.Zero
        return Rect(left, top, right, bottom)
    }

    fun visibleFraction(
        fullBounds: Rect,
        clippedBounds: Rect,
        viewport: Rect,
    ): Float {
        val width = fullBounds.width
        val height = fullBounds.height
        if (width < MIN_FRAME_PX || height < MIN_FRAME_PX) return 0f
        if (viewport.width <= 0f || viewport.height <= 0f) return 0f
        val visible = intersectionArea(clippedBounds, viewport)
        if (visible <= 0f) return 0f
        return (visible / (width * height)).coerceIn(0f, 1f)
    }

    fun isOnScreen(
        boundsInWindow: Rect,
        windowWidth: Float,
        windowHeight: Float,
    ): Boolean = visibleFraction(
        fullBounds = boundsInWindow,
        clippedBounds = boundsInWindow,
        viewport = Rect(0f, 0f, windowWidth, windowHeight),
    ) >= MIN_VISIBLE_FRACTION

    fun isOnScreen(
        fullBounds: Rect,
        clippedBounds: Rect,
        viewport: Rect,
    ): Boolean = visibleFraction(fullBounds, clippedBounds, viewport) >= MIN_VISIBLE_FRACTION

    private fun intersectionArea(a: Rect, b: Rect): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        val w = (right - left).coerceAtLeast(0f)
        val h = (bottom - top).coerceAtLeast(0f)
        return w * h
    }
}

/**
 * Invokes [onVisible] after Bookie-parity viewability (50% + 200 ms dwell).
 * [onVisible] may be called more than once; impression de-dupe lives on the view model.
 */
@Composable
internal fun Modifier.reportWhenAdVisible(
    trackingKey: String = "",
    onVisible: () -> Unit,
): Modifier {
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val layoutDirection = LocalLayoutDirection.current
    val windowInsets = WindowInsets.safeDrawing
    var lastCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var lastLoggedVisible by remember { mutableStateOf<Boolean?>(null) }
    val tracker = remember { AdImpressionVisibilityTracker() }

    LaunchedEffect(trackingKey) {
        tracker.reset()
        lastLoggedVisible = null
    }

    fun viewportRect(): Rect {
        val d = density.density
        return AdVisibility.impressionViewportRect(
            screenWidthPx = configuration.screenWidthDp * d,
            screenHeightPx = configuration.screenHeightDp * d,
            insetLeft = windowInsets.getLeft(density, layoutDirection).toFloat(),
            insetTop = windowInsets.getTop(density).toFloat(),
            insetRight = windowInsets.getRight(density, layoutDirection).toFloat(),
            insetBottom = windowInsets.getBottom(density).toFloat(),
        )
    }

    fun snapshot(coordinates: LayoutCoordinates?): VisibilitySample? {
        val coords = coordinates ?: return null
        if (!coords.isAttached) return null
        val appActive = ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)
        val full = coords.boundsInWindow(clipBounds = false)
        val clipped = coords.boundsInWindow(clipBounds = true)
        when {
            full.width <= 0f || full.height <= 0f -> return null
            else -> Unit
        }
        val viewport = viewportRect()
        val fraction = AdVisibility.visibleFraction(full, clipped, viewport)
        val visible = appActive && fraction >= AdVisibility.MIN_VISIBLE_FRACTION
        if (lastLoggedVisible != visible) {
            lastLoggedVisible = visible
            val percent = (fraction * 100).toInt()
            AdLog.i(
                "visibility ${if (visible) "on" else "off"} $percent% " +
                    "started=$appActive bounds=$clipped viewport=$viewport",
            )
        }
        val tracking = if (clipped.width > 0f && clipped.height > 0f) clipped else full
        val d = density.density
        return VisibilitySample(
            fraction = fraction,
            centerY = tracking.center.y / d,
            width = full.width / d,
            height = full.height / d,
            appActive = appActive,
        )
    }

    LaunchedEffect(lifecycleOwner, trackingKey, configuration.screenWidthDp, configuration.screenHeightDp) {
        while (isActive && !tracker.hasQualified) {
            delay(AdImpressionPolicy.SAMPLE_PERIOD_MS)
            val sample = snapshot(lastCoordinates) ?: continue
            val nowSeconds = SystemClock.elapsedRealtime() / 1_000.0
            val qualified = tracker.update(
                visibleFraction = sample.fraction,
                centerY = sample.centerY,
                width = sample.width,
                height = sample.height,
                nowSeconds = nowSeconds,
                appActive = sample.appActive,
            )
            if (qualified) {
                onVisible()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                snapshot(lastCoordinates)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return onGloballyPositioned { coordinates ->
        lastCoordinates = coordinates
        snapshot(coordinates)
    }
}

private data class VisibilitySample(
    val fraction: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val appActive: Boolean,
)
