package com.betterads.model

/**
 * Ad creative format / size.
 * Matches iOS `AdFormat` and Bookie `PlacementAdSize`.
 */
enum class AdFormat(val rawValue: String) {
    COMPACT("compact"),
    BANNER("banner"),
    CARD("card"),
    INTERSTITIAL("interstitial");

    companion object {
        fun fromRaw(raw: String?): AdFormat? =
            entries.firstOrNull { it.rawValue.equals(raw, ignoreCase = true) }
    }
}
