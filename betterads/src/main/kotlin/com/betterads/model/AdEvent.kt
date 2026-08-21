package com.betterads.model

import com.betterads.BetterAdsSDK
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

@Serializable
enum class AdEventType {
    @SerialName("impression")
    IMPRESSION,

    @SerialName("click")
    CLICK,
}

/** A single impression or click queued for `POST /api/v1/events`. */
@Serializable
internal data class AdEvent(
    @SerialName("event_id") val eventId: String = UUID.randomUUID().toString(),
    val type: AdEventType,
    @SerialName("campaign_id") val campaignId: Int,
    @SerialName("occurred_at") val occurredAt: String = AdEventFormatters.now(),
    @SerialName("device_id") val deviceId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: String? = null,
    val locale: String? = null,
    @SerialName("cta_value") val ctaValue: String? = null,
    @SerialName("sdk_version") val sdkVersion: String = BetterAdsSDK.VERSION,
)

internal object AdEventFormatters {
    private val iso8601: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(java.time.ZoneOffset.UTC)

    fun now(): String = iso8601.format(Instant.now())
}

internal fun AdModel.campaignIdAsInt(): Int? {
    val value = campaignId.trim().toIntOrNull() ?: return null
    return value.takeIf { it > 0 }
}
