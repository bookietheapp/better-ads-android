package com.betterads.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * Slim Serve Design payload: one hero image plus a tap destination.
 * Mirrors iOS `AdModel`. NativeOS does not compose copy, colors, logo, or a CTA button.
 */
@Serializable
data class AdModel(
    @Serializable(with = FlexibleIdSerializer::class) val adId: String,
    @Serializable(with = FlexibleIdSerializer::class) val campaignId: String = "",
    val size: String,
    val images: AdImages,
    val ctaLink: String,
) {
    val format: AdFormat?
        get() = AdFormat.fromRaw(size)

    /** CTA destination inferred from [ctaLink] (https → URL, otherwise deeplink). */
    val ctaAction: AdCtaAction
        get() = AdCtaAction.from(ctaLink)

    companion object {
        fun previewFixture(size: AdFormat = AdFormat.BANNER): AdModel = AdModel(
            adId = "1",
            campaignId = "1",
            size = size.rawValue,
            images = AdImages(hero = heroUrls(size)),
            ctaLink = "https://example.com/offer",
        )

        private fun heroUrls(size: AdFormat): AdImageUrls = when (size) {
            AdFormat.COMPACT -> AdImageUrls(
                oneX = "https://picsum.photos/329/51",
                twoX = "https://picsum.photos/658/102",
                threeX = "https://picsum.photos/987/153",
            )
            AdFormat.BANNER -> AdImageUrls(
                oneX = "https://picsum.photos/345/164",
                twoX = "https://picsum.photos/690/328",
                threeX = "https://picsum.photos/1035/492",
            )
            AdFormat.CARD -> AdImageUrls(
                oneX = "https://picsum.photos/336/443",
                twoX = "https://picsum.photos/672/886",
                threeX = "https://picsum.photos/1008/1329",
            )
            AdFormat.INTERSTITIAL -> AdImageUrls()
        }
    }
}

@Serializable
data class AdImages(
    val hero: AdImageUrls,
)

/**
 * Signed image URL strings at 1x / 2x / 3x.
 * Pick the matching density; do not substitute another.
 */
@Serializable
data class AdImageUrls(
    @SerialName("1x") val oneX: String = "",
    @SerialName("2x") val twoX: String = "",
    @SerialName("3x") val threeX: String = "",
) {
    fun urlFor(density: Float): String? {
        val candidate = when {
            density >= 3f -> threeX
            density >= 2f -> twoX
            else -> oneX
        }
        return candidate.takeIf { it.isNotBlank() }
    }
}

@Serializable
data class AdCtaAction(
    val type: AdCtaActionType,
    val value: String,
) {
    companion object {
        fun from(ctaLink: String): AdCtaAction {
            val trimmed = ctaLink.trim()
            val scheme = runCatching { java.net.URI(trimmed).scheme }.getOrNull()?.lowercase()
            val type = if (scheme == "http" || scheme == "https") {
                AdCtaActionType.URL
            } else {
                AdCtaActionType.DEEPLINK
            }
            return AdCtaAction(type = type, value = trimmed)
        }
    }
}

@Serializable
enum class AdCtaActionType {
    @SerialName("url")
    URL,

    @SerialName("deeplink")
    DEEPLINK,
}

/** Accepts Serve ids encoded as a JSON string or number. */
internal object FlexibleIdSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        val primitive = jsonDecoder.decodeJsonElement() as? JsonPrimitive
            ?: return decoder.decodeString()
        return primitive.content
    }
}
