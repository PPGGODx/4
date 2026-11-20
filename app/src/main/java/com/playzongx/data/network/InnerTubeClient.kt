package com.playzongx.data.network

import com.playzongx.domain.SearchResult
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Minimal client for YouTube Music's Internal InnerTube search endpoint.
 *
 * The request is always executed on [Dispatchers.IO]. Transport failures and
 * non-2xx responses surface as [MusicError.Network]; malformed response bodies
 * surface as [MusicError.Parse].
 */
class InnerTubeClient(
    private val http: OkHttpClient,
    private val endpoint: String = DEFAULT_ENDPOINT,
) {

    suspend fun search(query: String): SearchResult = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", CLIENT_NAME)
                    put("clientVersion", CLIENT_VERSION)
                    put("hl", "th")
                    put("gl", "TH")
                }
            }
            put("query", query)
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", USER_AGENT)
            .header("X-YouTube-Client-Name", CLIENT_NAME_HEADER)
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .build()

        val response = try {
            http.newCall(request).execute()
        } catch (error: IOException) {
            throw MusicError.Network(error)
        }

        response.use { result ->
            if (!result.isSuccessful) {
                throw MusicError.Network(
                    IOException("HTTP ${result.code} from ${result.request.url}"),
                )
            }

            val body = try {
                result.body?.string().orEmpty()
            } catch (error: IOException) {
                throw MusicError.Network(error)
            }

            val root = try {
                Json.parseToJsonElement(body) as? JsonObject
                    ?: throw IllegalArgumentException("Expected a JSON object response")
            } catch (error: Exception) {
                throw MusicError.Parse(error)
            }

            SearchMapper.parse(root)
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT =
            "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"

        private const val CLIENT_NAME = "WEB_REMIX"
        private const val CLIENT_VERSION = "1.20250101.00.00"
        private const val CLIENT_NAME_HEADER = "67"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
