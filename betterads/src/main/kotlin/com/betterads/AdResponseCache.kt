package com.betterads

import com.betterads.model.AdModel
import com.betterads.model.AdType
import java.util.concurrent.ConcurrentHashMap

/** Process-scoped creative cache so remounted placements can paint without a blank loading flash. */
internal class AdResponseCache {
    private val adsByType = ConcurrentHashMap<String, AdModel>()

    fun ad(forType: AdType): AdModel? = adsByType[forType.rawValue]

    fun store(ad: AdModel, forType: AdType) {
        adsByType[forType.rawValue] = ad
    }

    fun remove(forType: AdType) {
        adsByType.remove(forType.rawValue)
    }
}
