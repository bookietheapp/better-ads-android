package com.betterads.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ad creative payload — mirrors iOS `AdModel` / Bookie `PlacementAdResponse`.
 */
@Serializable
data class AdModel(
    val campaignId: String = "",
    val size: String,
    val brand: String,
    val backgroundColor: String,
    val textColor: String,
    val headline: String,
    val description: String,
    val images: AdImages,
    val cta: AdCta,
) {
    val format: AdFormat?
        get() = AdFormat.fromRaw(size)

    companion object {
        fun previewFixture(size: AdFormat = AdFormat.BANNER): AdModel = AdModel(
            campaignId = "sample-campaign-01",
            size = size.rawValue,
            brand = "Sample Brand",
            backgroundColor = "#CC96FF",
            textColor = "#000000",
            headline = "Sample Brand",
            description = "60 days *free* · try it today\nUse code *sample*",
            images = AdImages(
                hero = AdImageUrls(
                    oneX = "https://picsum.photos/205/164",
                    twoX = "https://picsum.photos/410/328",
                    threeX = "https://picsum.photos/615/492",
                ),
                icon = AdImageUrls(
                    oneX = "https://picsum.photos/109/17",
                    twoX = "https://picsum.photos/218/34",
                    threeX = "https://picsum.photos/327/51",
                ),
            ),
            cta = AdCta(
                title = "Learn more",
                ctaButtonColor = "#FFFFFF",
                ctaTitleColor = "#000000",
                action = AdCtaAction(
                    type = AdCtaActionType.URL,
                    value = "https://example.com/offer",
                ),
            ),
        )
    }
}

@Serializable
data class AdImages(
    val hero: AdImageUrls,
    val icon: AdImageUrls,
)

@Serializable
data class AdImageUrls(
    @SerialName("1x") val oneX: String = "",
    @SerialName("2x") val twoX: String = "",
    @SerialName("3x") val threeX: String = "",
) {
    fun urlFor(density: Float): String? {
        val candidate = when {
            density >= 3f && threeX.isNotEmpty() -> threeX
            density >= 2f && twoX.isNotEmpty() -> twoX
            oneX.isNotEmpty() -> oneX
            twoX.isNotEmpty() -> twoX
            threeX.isNotEmpty() -> threeX
            else -> null
        }
        return candidate?.takeIf { it.isNotBlank() }
    }
}

@Serializable
data class AdCta(
    val title: String,
    val ctaButtonColor: String,
    val ctaTitleColor: String,
    val action: AdCtaAction,
)

@Serializable
data class AdCtaAction(
    val type: AdCtaActionType,
    val value: String,
)

@Serializable
enum class AdCtaActionType {
    @SerialName("url")
    URL,

    @SerialName("deeplink")
    DEEPLINK,
}
