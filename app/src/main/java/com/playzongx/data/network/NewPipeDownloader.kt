package com.playzongx.data.network

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

/**
 * NewPipeExtractor's transport, backed by the app's shared [OkHttpClient].
 *
 * NewPipe supplies a method, URL, headers and an optional byte body; the
 * OkHttp response is mapped back into NewPipe's own [Response] value type.
 */
class NewPipeDownloader(private val http: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val headers = request.headers()
        val contentType = headers["Content-Type"]?.firstOrNull()?.toMediaTypeOrNull()

        val builder = okhttp3.Request.Builder().url(request.url())
        headers.forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }

        val method = request.httpMethod().uppercase()
        val body = request.dataToSend()?.let { it.toRequestBody(contentType) }
        when (method) {
            "GET" -> builder.get()
            "HEAD" -> builder.head()
            "POST" -> builder.post(body ?: EMPTY_BODY.toRequestBody(contentType))
            "PUT" -> builder.put(body ?: EMPTY_BODY.toRequestBody(contentType))
            "PATCH" -> builder.patch(body ?: EMPTY_BODY.toRequestBody(contentType))
            "DELETE" -> builder.delete(body)
            else -> builder.method(method, body)
        }

        http.newCall(builder.build()).execute().use { response ->
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string().orEmpty(),
                request.url(),
            )
        }
    }

    private companion object {
        val EMPTY_BODY = ByteArray(0)
    }
}
