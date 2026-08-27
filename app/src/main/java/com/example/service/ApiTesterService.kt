package com.example.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Layanan API Tester (Postman Mobile Client):
 * Mengirim request HTTP GET, POST, PUT, DELETE, PATCH dengan custom headers dan JSON body.
 */
class ApiTesterService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun executeRequest(
        url: String,
        method: String,
        headersMap: Map<String, String>,
        bodyContent: String?
    ): ApiResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val headersBuilder = Headers.Builder()
            headersMap.forEach { (k, v) ->
                if (k.isNotBlank()) headersBuilder.add(k.trim(), v.trim())
            }

            val requestBuilder = Request.Builder()
                .url(url.trim())
                .headers(headersBuilder.build())

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = bodyContent?.takeIf { it.isNotBlank() }?.toRequestBody(mediaType)

            when (method.uppercase()) {
                "GET" -> requestBuilder.get()
                "POST" -> requestBuilder.post(requestBody ?: "".toRequestBody(mediaType))
                "PUT" -> requestBuilder.put(requestBody ?: "".toRequestBody(mediaType))
                "DELETE" -> {
                    if (requestBody != null) requestBuilder.delete(requestBody)
                    else requestBuilder.delete()
                }
                "PATCH" -> requestBuilder.patch(requestBody ?: "".toRequestBody(mediaType))
                else -> requestBuilder.get()
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val duration = System.currentTimeMillis() - startTime
            val rawBody = response.body?.string() ?: ""

            // Format pretty JSON jika memungkinkan
            val prettyBody = formatJson(rawBody)

            val headersList = response.headers.names().map { "$it: ${response.headers[it]}" }

            ApiResponse(
                statusCode = response.code,
                statusMessage = response.message,
                durationMs = duration,
                body = prettyBody,
                headers = headersList,
                isSuccess = response.isSuccessful
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            ApiResponse(
                statusCode = 0,
                statusMessage = "Error",
                durationMs = duration,
                body = "Request Failed:\n${e.message}",
                headers = emptyList(),
                isSuccess = false
            )
        }
    }

    private fun formatJson(raw: String): String {
        return try {
            val trimmed = raw.trim()
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed).toString(2)
            } else if (trimmed.startsWith("[")) {
                JSONArray(trimmed).toString(2)
            } else {
                raw
            }
        } catch (e: Exception) {
            raw
        }
    }
}

data class ApiResponse(
    val statusCode: Int,
    val statusMessage: String,
    val durationMs: Long,
    val body: String,
    val headers: List<String>,
    val isSuccess: Boolean
)
