package com.betterads.model

/**
 * Identifies which ad to fetch — path / query segment equivalent to iOS `AdType`.
 */
@JvmInline
value class AdType(val rawValue: String) {
    constructor(format: AdFormat) : this(format.rawValue)

    override fun toString(): String = rawValue
}
