package com.betterads

import com.betterads.model.AdModel
import com.betterads.model.AdType
import com.betterads.model.BetterAdsError
import java.util.concurrent.ConcurrentHashMap

/** Publisher-owned External Ad Id for keyed Serve. Empty / whitespace is treated as omitted. */
internal object ExternalAdId {
    fun normalize(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        return trimmed.takeIf { it.isNotEmpty() }
    }
}

internal fun isNoEligibleAd(error: Throwable): Boolean = when (error) {
    is BetterAdsError.UnknownAdType -> true
    is BetterAdsError.HttpStatus -> error.code == 404
    else -> false
}

/** Process-scoped creative cache so remounted placements can paint without a blank loading flash. */
internal class AdResponseCache {
    private val adsByKey = ConcurrentHashMap<String, AdModel>()

    fun ad(type: AdType, externalAdId: String? = null): AdModel? = adsByKey[key(type, externalAdId)]

    fun store(ad: AdModel, type: AdType, externalAdId: String? = null) {
        adsByKey[key(type, externalAdId)] = ad
    }

    fun remove(type: AdType, externalAdId: String? = null) {
        adsByKey.remove(key(type, externalAdId))
    }

    companion object {
        fun key(type: AdType, externalAdId: String?): String {
            val id = ExternalAdId.normalize(externalAdId)
            return if (id != null) "${type.rawValue}#$id" else type.rawValue
        }
    }
}
