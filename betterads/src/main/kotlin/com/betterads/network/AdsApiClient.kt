package com.betterads.network

import com.betterads.BetterAdsAuthProviding
import com.betterads.BetterAdsConfiguration
import com.betterads.BetterAdsContentMode
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
    private val httpClient: HttpClient,
    private val authProvider: BetterAdsAuthProviding? = null,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false },
) {
    suspend fun fetchAd(type: AdType): AdModel {
        val (path, query) = when (configuration.contentMode) {
            BetterAdsContentMode.BOOKIE_GET_AD ->
                "/getAd" to mapOf("size" to type.rawValue)
            BetterAdsContentMode.DEDICATED_API ->
                "/ads/${type.rawValue}" to emptyMap()
            BetterAdsContentMode.FIXTURE ->
                throw BetterAdsError.Transport("Fixture mode does not use HTTP fetch")
        }

        val request = makeRequest("GET", path, query)
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

    suspend fun postImpression(type: AdType) {
        post(
            path = "/ads/${type.rawValue}/impressions",
            body = AnalyticsEnvelope(
                sessionId = configuration.sessionId,
                userId = configuration.userId,
                locale = configuration.locale.toLanguageTag(),
                ctaValue = null,
            ),
        )
    }

    suspend fun postClick(type: AdType, ctaValue: String) {
        post(
            path = "/ads/${type.rawValue}/clicks",
            body = AnalyticsEnvelope(
                sessionId = configuration.sessionId,
                userId = configuration.userId,
                locale = configuration.locale.toLanguageTag(),
                ctaValue = ctaValue,
            ),
        )
    }

    private suspend fun post(path: String, body: AnalyticsEnvelope) {
        val bytes = json.encodeToString(AnalyticsEnvelope.serializer(), body).toByteArray()
        val request = makeRequest(
            method = "POST",
            path = path,
            query = emptyMap(),
            extraHeaders = mapOf("Content-Type" to "application/json"),
            body = bytes,
        )
        val response = httpClient.send(request)
        if (response.code !in 200..299) {
            throw BetterAdsError.HttpStatus(
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
        val base = configuration.baseUrl?.trimEnd('/')
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
internal data class AnalyticsEnvelope(
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val locale: String,
    @SerialName("cta_value") val ctaValue: String? = null,
)
