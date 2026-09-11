package com.betterads.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdVisibilityTest {
    @Test
    fun fullyOnScreen_isVisible() {
        assertTrue(
            AdVisibility.isOnScreen(
                boundsInWindow = Rect(22f, 200f, 367f, 364f),
                windowWidth = 390f,
                windowHeight = 844f,
            ),
        )
    }

    @Test
    fun fullyBelowFold_isNotVisible() {
        assertFalse(
            AdVisibility.isOnScreen(
                boundsInWindow = Rect(22f, 900f, 367f, 1064f),
                windowWidth = 390f,
                windowHeight = 844f,
            ),
        )
    }

    @Test
    fun exactlyHalfVisible_isVisible() {
        // Banner height 164; 82px on screen = 50%.
        assertTrue(
            AdVisibility.isOnScreen(
                boundsInWindow = Rect(22f, 762f, 367f, 926f),
                windowWidth = 390f,
                windowHeight = 844f,
            ),
        )
    }

    @Test
    fun justUnderHalfVisible_isNotVisible() {
        assertFalse(
            AdVisibility.isOnScreen(
                boundsInWindow = Rect(22f, 763f, 367f, 927f),
                windowWidth = 390f,
                windowHeight = 844f,
            ),
        )
    }

    @Test
    fun zeroSize_isNotVisible() {
        assertFalse(AdVisibility.isOnScreen(Rect.Zero, 390f, 844f))
        assertFalse(
            AdVisibility.isOnScreen(
                boundsInWindow = Rect(0f, 0f, 345f, 164f),
                windowWidth = 0f,
                windowHeight = 0f,
            ),
        )
    }

    @Test
    fun collapsedSliver_isNotVisible() {
        // 954x3 overlay collapse — 100% of that sliver is on screen, but it is not a real ad.
        assertFalse(
            AdVisibility.isOnScreen(
                boundsInWindow = Rect(63f, 136f, 1017f, 139f),
                windowWidth = 1080f,
                windowHeight = 2400f,
            ),
        )
    }

    @Test
    fun clippedAgainstSafeViewport_usesUnclippedAreaAsDenominator() {
        val full = Rect(0f, 100f, 100f, 300f) // 200px tall
        val clipped = Rect(0f, 100f, 100f, 150f) // 50px visible after clip
        val viewport = Rect(0f, 80f, 390f, 800f)
        // 50*100 / (100*200) = 0.25
        assertFalse(AdVisibility.isOnScreen(full, clipped, viewport))
        val halfClipped = Rect(0f, 100f, 100f, 200f) // 100px of 200 = 50%
        assertTrue(AdVisibility.isOnScreen(full, halfClipped, viewport))
    }

    @Test
    fun safeDrawingViewport_excludesSystemBars() {
        val viewport = AdVisibility.impressionViewportRect(
            screenWidthPx = 1080f,
            screenHeightPx = 2400f,
            insetLeft = 0f,
            insetTop = 80f,
            insetRight = 0f,
            insetBottom = 120f,
        )
        val inNavBar = Rect(0f, 2300f, 1080f, 2390f)
        assertFalse(AdVisibility.isOnScreen(inNavBar, inNavBar, viewport))
        val inContent = Rect(40f, 200f, 1040f, 360f)
        assertTrue(AdVisibility.isOnScreen(inContent, inContent, viewport))
    }

    @Test
    fun tracker_qualifiesAfterDwellOnceWarmupEnds() {
        val tracker = AdImpressionVisibilityTracker()
        val t0 = 10.0
        assertFalse(tracker.update(1f, 200f, 345f, 164f, t0, true))
        assertFalse(tracker.update(1f, 200f, 345f, 164f, t0 + 0.34, true))
        assertTrue(tracker.update(1f, 200f, 345f, 164f, t0 + 0.56, true))
        assertFalse(tracker.update(1f, 200f, 345f, 164f, t0 + 0.90, true))
    }

    @Test
    fun tracker_resetsDwellWhenVisibilityDrops() {
        val tracker = AdImpressionVisibilityTracker()
        val t0 = 10.0
        assertFalse(tracker.update(1f, 200f, 345f, 164f, t0, true))
        assertFalse(tracker.update(0.2f, 200f, 345f, 164f, t0 + 0.40, true))
        assertFalse(tracker.update(1f, 200f, 345f, 164f, t0 + 0.41, true))
        assertTrue(tracker.update(1f, 200f, 345f, 164f, t0 + 0.62, true))
    }

    @Test
    fun tracker_resetAllowsSecondQualification() {
        val tracker = AdImpressionVisibilityTracker()
        val t0 = 10.0
        assertFalse(tracker.update(1f, 200f, 345f, 164f, t0, true))
        assertTrue(tracker.update(1f, 200f, 345f, 164f, t0 + 0.56, true))
        tracker.reset()
        assertFalse(tracker.update(1f, 200f, 345f, 164f, t0 + 0.57, true))
        assertTrue(tracker.update(1f, 200f, 345f, 164f, t0 + 1.13, true))
    }

    @Test
    fun tracker_horizontalMotionDoesNotBlockVerticalDwell() {
        val tracker = AdImpressionVisibilityTracker()
        val t0 = 10.0
        assertFalse(tracker.update(1f, 200f, 345f, 164f, t0, true))
        // Fast horizontal carousel would have blocked 2D hypot; vertical Y is unchanged.
        assertTrue(tracker.update(1f, 200f, 345f, 164f, t0 + 0.56, true))
    }

    @Test
    fun tracker_fastVerticalScrollPausesDwell() {
        val tracker = AdImpressionVisibilityTracker()
        val t0 = 10.0
        assertFalse(tracker.update(1f, 200f, 345f, 164f, t0, true))
        assertFalse(tracker.update(1f, 200f, 345f, 164f, t0 + 0.34, true))
        // 300pt in 20ms = 15_000 pt/s > 1200 — pause, keep dwell at 0.
        assertFalse(tracker.update(1f, 500f, 345f, 164f, t0 + 0.36, true))
        assertFalse(tracker.update(1f, 500f, 345f, 164f, t0 + 0.50, true))
        assertTrue(tracker.update(1f, 500f, 345f, 164f, t0 + 0.58, true))
    }
}
