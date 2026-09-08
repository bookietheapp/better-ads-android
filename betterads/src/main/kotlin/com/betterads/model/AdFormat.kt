package com.betterads.model

/**
 * Ad creative format / size — NativeOS Template name (`compact` / `banner` / `card`).
 * Matches iOS `AdFormat`. Serve has no interstitial template.
 */
enum class AdFormat(val rawValue: String) {
    COMPACT("compact"),
    BANNER("banner"),
    CARD("card"),
    /** Kept for source compatibility; Serve has no interstitial and no layout is rendered. */
    INTERSTITIAL("interstitial");

    companion object {
        fun fromRaw(raw: String?): AdFormat? =
            entries.firstOrNull { it.rawValue.equals(raw, ignoreCase = true) }
    }
}
