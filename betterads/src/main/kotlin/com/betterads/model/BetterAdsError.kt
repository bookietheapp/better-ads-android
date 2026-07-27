package com.betterads.model

sealed class BetterAdsError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    data object InvalidBaseUrl : BetterAdsError("BetterAds base URL is invalid.")
    data object InvalidResponse : BetterAdsError("BetterAds received an invalid HTTP response.")
    data class HttpStatus(val code: Int, val body: String?) :
        BetterAdsError("BetterAds request failed with status $code${body?.let { ": $it" } ?: ""}")

    data class DecodingFailed(val detail: String) :
        BetterAdsError("BetterAds failed to decode response: $detail")

    data class Transport(val detail: String) :
        BetterAdsError("BetterAds transport error: $detail")

    data class UnknownAdType(val type: AdType) :
        BetterAdsError("BetterAds unknown ad type: ${type.rawValue}")
}
