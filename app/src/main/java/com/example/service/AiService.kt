package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Layanan integrasi Gemini 1.5/3.5 Flash REST API.
 * Mendukung Streaming, Code Completion, Auto-Fix, Commit Message, Test Generator,
 * AI UI Designer, AI Code Reviewer, dan Vibe Coding.
 */
class AiService(private val context: Context) {

    private val preferencesManager = PreferencesManager(context)
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val modelName = "gemini-3.5-flash"
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelName"

    private fun getApiKey(): String {
        return preferencesManager.geminiKey
    }

    /**
     * Test Koneksi API Gemini
     */
    suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Pair(false, "Gemini API Key kosong. Masukkan di Settings.")
        }

        try {
            val response = generateContent("Halo Gemini! Balas dengan kata 'OK' untuk verifikasi koneksi.")
            if (response.isNotBlank() && !response.startsWith("Error:")) {
                Pair(true, "Koneksi Gemini API Berhasil! Model: $modelName")
            } else {
                Pair(false, response)
            }
        } catch (e: Exception) {
            Pair(false, "Gagal terhubung ke Gemini: ${e.message}")
        }
    }

    /**
     * Generate Konten Teks biasa
     */
    suspend fun generateContent(
        prompt: String,
        systemInstruction: String? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext "Error: Gemini API Key belum diatur di Settings."
        }

        val url = "$baseUrl:generateContent?key=$apiKey"

        val requestBodyJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            if (!systemInstruction.isNullOrBlank()) {
                val sysObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstruction) })
                    }
                    put("parts", parts)
                }
                put("systemInstruction", sysObj)
            }
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext "Error (${response.code}): $bodyString"
                }

                val json = JSONObject(bodyString)
                val candidates = json.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "Tidak ada respon.")
                    }
                }
                "Tidak ada teks respon dari Gemini."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal generateContent", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Streaming Respon Gemini
     */
    suspend fun generateContentStream(
        prompt: String,
        systemInstruction: String? = null,
        onChunk: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            val errorMsg = "Error: Gemini API Key belum diatur di Settings."
            onChunk(errorMsg)
            return@withContext errorMsg
        }

        val url = "$baseUrl:streamGenerateContent?key=$apiKey"

        val requestBodyJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            if (!systemInstruction.isNullOrBlank()) {
                val sysObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstruction) })
                    }
                    put("parts", parts)
                }
                put("systemInstruction", sysObj)
            }
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val fullResponse = StringBuilder()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = "Error (${response.code}): ${response.body?.string()}"
                    onChunk(err)
                    return@withContext err
                }

                val source = response.body?.source() ?: return@withContext "Empty body"
                val reader = source.inputStream().bufferedReader()
                var line: String?

                val buffer = StringBuilder()
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    buffer.append(currentLine)

                    // Format stream Gemini mengembalikan array JSON objek chunk
                    try {
                        var cleaned = buffer.toString().trim()
                        if (cleaned.startsWith("[")) cleaned = cleaned.substring(1)
                        if (cleaned.endsWith(",")) cleaned = cleaned.substring(0, cleaned.length - 1)
                        if (cleaned.endsWith("]")) cleaned = cleaned.substring(0, cleaned.length - 1)

                        if (cleaned.isNotBlank()) {
                            val chunkJson = JSONObject(cleaned)
                            val candidates = chunkJson.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val text = candidates.getJSONObject(0)
                                    .optJSONObject("content")
                                    ?.optJSONArray("parts")
                                    ?.optJSONObject(0)
                                    ?.optString("text")

                                if (!text.isNullOrBlank()) {
                                    fullResponse.append(text)
                                    withContext(Dispatchers.Main) {
                                        onChunk(text)
                                    }
                                    buffer.clear()
                                }
                            }
                        }
                    } catch (ignore: Exception) {
                        // Lanjutkan membaca jika chunk JSON belum lengkap
                    }
                }
            }
            fullResponse.toString().ifBlank { "Selesai." }
        } catch (e: Exception) {
            val err = "Error saat streaming: ${e.message}"
            withContext(Dispatchers.Main) { onChunk(err) }
            err
        }
    }

    /**
     * AI UI Designer: Analisis Gambar / Screenshot Desain UI -> Generate XML/Compose
     */
    suspend fun generateUiFromImage(
        bitmap: Bitmap,
        prompt: String = "Buatkan layout XML Android dan Jetpack Compose Composable yang persis dari gambar ini dengan styling Material 3 modern."
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return@withContext "Error: Gemini API Key kosong."

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

        val url = "$baseUrl:generateContent?key=$apiKey"
        val requestBodyJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                        put(JSONObject().apply {
                            val inlineData = JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64Image)
                            }
                            put("inlineData", inlineData)
                        })
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) return@withContext "Error: $bodyString"
                val json = JSONObject(bodyString)
                val text = json.optJSONArray("candidates")?.getJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.getJSONObject(0)
                    ?.optString("text") ?: "Tidak ada hasil."
                text
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * AI Code Completion: Menganalisa 5 baris kode terakhir dan memberikan saran completion
     */
    suspend fun getCodeCompletion(
        codeContext: String,
        fileExtension: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            Kamu adalah AI Code Autocomplete untuk bahasa $fileExtension.
            Berdasarkan kode berikut (fokus pada 5 baris terakhir):
            ```
            $codeContext
            ```
            Berikan KELANJUTAN kode yang paling tepat secara singkat (1 sampai 3 baris saja).
            HANYA kembalikan potongan kode kelanjutan tanpa penjelasan, tanpa markdown tick ```.
        """.trimIndent()

        val result = generateContent(prompt)
        result.replace("```", "").trim()
    }

    /**
     * AI Commit Message: Menghasilkan 3 opsi commit (feat, fix, refactor) dari git diff
     */
    suspend fun generateCommitMessages(diff: String): List<String> = withContext(Dispatchers.IO) {
        val prompt = """
            Analisis git diff berikut dan buatkan 3 opsi commit message berbahasa Inggris sesuai Conventional Commits:
            1. feat: ...
            2. fix: ...
            3. refactor: ...

            Diff:
            $diff

            Format output harus persis 3 baris:
            feat: deskripsi
            fix: deskripsi
            refactor: deskripsi
        """.trimIndent()

        val result = generateContent(prompt)
        val lines = result.lines().filter { it.isNotBlank() }
        if (lines.size >= 3) {
            lines.take(3)
        } else {
            listOf(
                "feat: update project features and UI",
                "fix: resolve syntax issues and improve stability",
                "refactor: optimize codebase architecture"
            )
        }
    }

    /**
     * AI Tester: Generate Unit & Robolectric Tests untuk file aktif
     */
    suspend fun generateTestsForFile(
        fileName: String,
        fileContent: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            Kamu adalah Android QA Engineer. Buatkan unit test lengkap menggunakan JUnit4 dan Robolectric untuk file '$fileName' berikut:
            ```kotlin
            $fileContent
            ```
            Sertakan assertions yang komprehensif, pastikan tidak ada syntax error.
            Kembalikan kode test Kotlin lengkap yang siap disimpan ke folder app/src/test/java/com/example/.
        """.trimIndent()

        generateContent(prompt)
    }

    /**
     * Auto Fix: Menganalisis log error build dan memberikan perbaikan kode
     */
    suspend fun generateAutoFix(
        errorLogs: String,
        sourceCode: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            Kamu adalah AI Auto-Fixer Android. Terjadi error saat build pada file terkait.
            Log Error:
            $errorLogs

            Kode Asli:
            $sourceCode

            Tugasmu: Perbaiki error tersebut.
            Kembalikan KODE LENGKAP yang sudah diperbaiki sehingga siap langsung menggantikan file asli.
        """.trimIndent()

        generateContent(prompt)
    }

    /**
     * AI Code Reviewer Otomatis (Feature 23)
     */
    suspend fun reviewCodeDiff(diffOrCode: String): Triple<Int, String, String> = withContext(Dispatchers.IO) {
        val prompt = """
            Kamu Senior Android Dev. Review kode ini. Beri 3 saran perbaikan, cari potensi memory leak, dan kasih skor Code Quality 1-10. Bahasa Indonesia.
            Kode / Diff:
            $diffOrCode

            Format Output:
            SKOR: [angka 1-10]
            SARAN:
            - [Saran 1]
            - [Saran 2]
            - [Saran 3]
            MEMORY_LEAKS:
            [Potensi memory leak atau 'Aman, tidak ditemukan memory leak']
        """.trimIndent()

        val response = generateContent(prompt)
        var score = 8
        val scoreMatcher = Regex("SKOR:\\s*(\\d+)").find(response)
        if (scoreMatcher != null) {
            score = scoreMatcher.groupValues[1].toIntOrNull() ?: 8
        }

        Triple(score, response, "AI Review")
    }

    /**
     * Vibe Coding: Multi-file generator dari instruksi suara
     */
    suspend fun generateFullFeatureVibe(voiceInstruction: String): String = withContext(Dispatchers.IO) {
        val prompt = """
            Mode Vibe Coding: Pengguna memberikan instruksi suara berikut:
            "$voiceInstruction"

            Buatkan rancangan fitur lengkap untuk Android Jetpack Compose.
            Sertakan file yang dibutuhkan dengan format:
            FILE: [path/ke/file]
            ```kotlin
            [isi file]
            ```
        """.trimIndent()

        generateContent(prompt)
    }

    companion object {
        private const val TAG = "AiService"
    }
}
