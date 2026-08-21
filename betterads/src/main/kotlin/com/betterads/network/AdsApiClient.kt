package com.betterads.network

import android.util.Log
import com.betterads.BetterAdsAuthProviding
import com.betterads.BetterAdsConfiguration
import com.betterads.BetterAdsContentMode
import com.betterads.BetterAdsEndpoints
import com.betterads.BetterAdsIdentityStore
import com.betterads.model.AdEvent
import com.betterads.model.AdModel
import com.betterads.model.AdType
import com.betterads.model.BetterAdsError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

interface HttpClient {
    suspend fun send(request: HttpRequest): HttpResponse
}

data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)

data class HttpResponse(
    val code: Int,
    val body: ByteArray,
)

class UrlConnectionHttpClient : HttpClient {
    override suspend fun send(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = request.method
                connectTimeout = 30_000
                readTimeout = 30_000
                doInput = true
                request.headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (request.body != null) {
                    doOutput = true
                    outputStream.use { it.write(request.body) }
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.readBytes() ?: ByteArray(0)
            connection.disconnect()
            HttpResponse(code, bytes)
        } catch (e: Exception) {
            throw BetterAdsError.Transport(e.message ?: e.toString())
        }
    }
}

internal class AdsApiClient(
    private val configuration: BetterAdsConfiguration,
    private val identity: BetterAdsIdentityStore,
    private val httpClient: HttpClient,
    private val authProvider: BetterAdsAuthProviding? = null,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false },
) {
    suspend fun fetchAd(type: AdType): AdModel {
        val (path, query) = when (configuration.contentMode) {
            BetterAdsContentMode.BOOKIE_GET_AD ->
                "/getAd" to mapOf("size" to type.rawValue)
            BetterAdsContentMode.SERVE_V1 -> {
                // Transitional: backend resolves the app from API key once auth ships.
                val items = linkedMapOf<String, String>()
                configuration.appName?.takeIf { it.isNotEmpty() }?.let { items["app"] = it }
                items["size"] = type.rawValue
                "/api/v1/serve" to items
            }
            BetterAdsContentMode.DEDICATED_API ->
                "/ads/${type.rawValue}" to emptyMap()
            BetterAdsContentMode.FIXTURE ->
                throw BetterAdsError.Transport("Fixture mode does not use HTTP fetch")
        }

        val request = makeRequest("GET", path, query)
        Log.d("BetterAds", "GET ${request.url}")
        val response = httpClient.send(request)
        return when (response.code) {
            in 200..299 -> {
                try {
                    json.decodeFromString(AdModel.serializer(), response.body.decodeToString())
                } catch (e: Exception) {
                    throw BetterAdsError.DecodingFailed(e.message ?: e.toString())
                }
            }
            404 -> throw BetterAdsError.UnknownAdType(type)
            else -> throw BetterAdsError.HttpStatus(
                response.code,
                response.body.decodeToString().ifBlank { null },
            )
        }
    }

    private val eventsJson: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun postEvents(events: List<AdEvent>): EventsPostResult {
        val body = EventsRequestBody(events = events)
        val bytes = eventsJson.encodeToString(EventsRequestBody.serializer(), body).toByteArray()
        val request = makeRequest(
            method = "POST",
            path = BetterAdsEndpoints.EVENTS_PATH,
            query = emptyMap(),
            extraHeaders = mapOf("Content-Type" to "application/json"),
            body = bytes,
        )
        val response = httpClient.send(request)
        return when (response.code) {
            in 200..299 -> {
                try {
                    val decoded = json.decodeFromString(EventsApiResponse.serializer(), response.body.decodeToString())
                    EventsPostResult(
                        accepted = decoded.accepted,
                        rejected = decoded.rejected.map {
                            RejectedEventItem(
                                index = it.index,
                                eventId = it.eventId,
                                errors = it.errors.orEmpty(),
                            )
                        },
                    )
                } catch (e: Exception) {
                    throw BetterAdsError.DecodingFailed(e.message ?: e.toString())
                }
            }
            else -> throw BetterAdsError.HttpStatus(
                response.code,
                response.body.decodeToString().ifBlank { null },
            )
        }
    }

    private suspend fun makeRequest(
        method: String,
        path: String,
        query: Map<String, String>,
        extraHeaders: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
    ): HttpRequest {
        val base = configuration.resolvedBaseUrl()?.trimEnd('/')
            ?: throw BetterAdsError.InvalidBaseUrl
        val queryString = if (query.isEmpty()) {
            ""
        } else {
            "?" + query.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, Charsets.UTF_8)}=${URLEncoder.encode(v, Charsets.UTF_8)}"
            }
        }
        val url = try {
            URI("$base$path$queryString").toURL().toString()
        } catch (_: Exception) {
            throw BetterAdsError.InvalidBaseUrl
        }

        val headers = linkedMapOf(
            "Accept" to "application/json",
            "Accept-Language" to configuration.locale.toLanguageTag(),
        )
        if (configuration.apiKey.isNotEmpty()) {
            headers["X-API-Key"] = configuration.apiKey
        }
        authProvider?.bearerAccessToken()?.takeIf { it.isNotEmpty() }?.let {
            headers["Authorization"] = "Bearer $it"
        }
        headers.putAll(extraHeaders)

        return HttpRequest(method = method, url = url, headers = headers, body = body)
    }
}

@Serializable
private data class EventsRequestBody(
    val events: List<AdEvent>,
)

@Serializable
private data class EventsApiResponse(
    val ok: Boolean,
    val accepted: Int,
    val rejected: List<RejectedEventResponse> = emptyList(),
)

@Serializable
private data class RejectedEventResponse(
    val index: Int,
    @SerialName("event_id") val eventId: String? = null,
    val errors: List<String>? = null,
)
