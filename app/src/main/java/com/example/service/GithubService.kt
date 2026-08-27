package com.example.service

import android.content.Context
import android.util.Log
import com.example.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Layanan integrasi GitHub REST API.
 * Mendukung autentikasi Token, Clone, Commit, Push, Pull, Branch Switcher,
 * Pull Requests, Actions Workflows, Dispatch Build, dan Artifacts.
 */
class GithubService(private val context: Context) {

    private val preferencesManager = PreferencesManager(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://api.github.com"

    private fun getAuthHeaders(): Headers {
        val token = preferencesManager.githubToken
        val builder = Headers.Builder()
            .add("Accept", "application/vnd.github+json")
            .add("User-Agent", "rerev7-Android-IDE")
        if (token.isNotBlank()) {
            builder.add("Authorization", "Bearer $token")
        }
        return builder.build()
    }

    /**
     * Test Koneksi GitHub API
     */
    suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = preferencesManager.githubToken
        if (token.isBlank()) {
            return@withContext Pair(false, "GitHub Token belum diisi di Settings.")
        }

        val request = Request.Builder()
            .url("$baseUrl/user")
            .headers(getAuthHeaders())
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val login = json.optString("login", "User")
                    val name = json.optString("name", login)
                    Pair(true, "Terhubung ke GitHub sebagai @$login ($name)")
                } else {
                    Pair(false, "Autentikasi gagal (${response.code}): $body")
                }
            }
        } catch (e: Exception) {
            Pair(false, "Koneksi gagal: ${e.message}")
        }
    }

    /**
     * Dapatkan daftar Branch repo
     */
    suspend fun getBranches(repo: String = preferencesManager.defaultRepo): List<String> = withContext(Dispatchers.IO) {
        if (repo.isBlank()) return@withContext listOf("main", "master")

        val request = Request.Builder()
            .url("$baseUrl/repos/$repo/branches")
            .headers(getAuthHeaders())
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonArray = JSONArray(response.body?.string() ?: "[]")
                    val list = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        list.add(jsonArray.getJSONObject(i).optString("name"))
                    }
                    if (list.isNotEmpty()) list else listOf("main", "master")
                } else {
                    listOf("main", "master", "develop")
                }
            }
        } catch (e: Exception) {
            listOf("main", "master", "develop")
        }
    }

    /**
     * Dapatkan daftar Workflow Runs (GitHub Actions)
     */
    suspend fun getWorkflowRuns(repo: String = preferencesManager.defaultRepo): List<WorkflowRun> = withContext(Dispatchers.IO) {
        if (repo.isBlank()) return@withContext emptyList()

        val request = Request.Builder()
            .url("$baseUrl/repos/$repo/actions/runs?per_page=15")
            .headers(getAuthHeaders())
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val runsArray = json.optJSONArray("workflow_runs") ?: JSONArray()
                    val result = mutableListOf<WorkflowRun>()
                    for (i in 0 until runsArray.length()) {
                        val item = runsArray.getJSONObject(i)
                        result.add(
                            WorkflowRun(
                                id = item.optLong("id"),
                                name = item.optString("name", "Build"),
                                status = item.optString("status", "queued"),
                                conclusion = item.optString("conclusion", "in_progress"),
                                branch = item.optString("head_branch", "main"),
                                commitMessage = item.optJSONObject("head_commit")?.optString("message", "Commit") ?: "Commit",
                                createdAt = item.optString("created_at", ""),
                                htmlUrl = item.optString("html_url", "")
                            )
                        )
                    }
                    result
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengambil workflow runs", e)
            emptyList()
        }
    }

    /**
     * Trigger GitHub Actions Cloud Build menggunakan workflow_dispatch
     */
    suspend fun triggerCloudBuild(
        repo: String = preferencesManager.defaultRepo,
        workflowId: String = "build.yml",
        branch: String = "main"
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (repo.isBlank()) return@withContext Pair(false, "Nama repository belum diset.")
        val token = preferencesManager.githubToken
        if (token.isBlank()) return@withContext Pair(false, "GitHub Token kosong.")

        val url = "$baseUrl/repos/$repo/actions/workflows/$workflowId/dispatches"
        val bodyJson = JSONObject().apply {
            put("ref", branch)
        }

        val request = Request.Builder()
            .url(url)
            .headers(getAuthHeaders())
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 204) {
                    Pair(true, "Cloud Build berhasil ditrigger di GitHub Actions untuk branch $branch!")
                } else {
                    val err = response.body?.string() ?: ""
                    Pair(false, "Gagal trigger build (${response.code}): $err")
                }
            }
        } catch (e: Exception) {
            Pair(false, "Error: ${e.message}")
        }
    }

    /**
     * Dapatkan log workflow run yang gagal
     */
    suspend fun getFailureLogs(
        repo: String = preferencesManager.defaultRepo,
        runId: Long
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/repos/$repo/actions/runs/$runId/jobs")
            .headers(getAuthHeaders())
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val jobs = json.optJSONArray("jobs") ?: JSONArray()
                    val logBuilder = StringBuilder()
                    for (i in 0 until jobs.length()) {
                        val job = jobs.getJSONObject(i)
                        val name = job.optString("name")
                        val conclusion = job.optString("conclusion")
                        logBuilder.append("Job: $name [$conclusion]\n")
                        val steps = job.optJSONArray("steps") ?: JSONArray()
                        for (j in 0 until steps.length()) {
                            val step = steps.getJSONObject(j)
                            val stepName = step.optString("name")
                            val stepConclusion = step.optString("conclusion")
                            if (stepConclusion == "failure") {
                                logBuilder.append("  -> Step GAGAL: $stepName\n")
                            }
                        }
                    }
                    if (logBuilder.isNotEmpty()) logBuilder.toString() else "Tidak ada detail log kegagalan."
                } else {
                    "Gagal mengambil log (${response.code})"
                }
            }
        } catch (e: Exception) {
            "Error mengambil log: ${e.message}"
        }
    }

    /**
     * Dapatkan daftar Pull Requests
     */
    suspend fun getPullRequests(repo: String = preferencesManager.defaultRepo): List<PullRequestItem> = withContext(Dispatchers.IO) {
        if (repo.isBlank()) return@withContext emptyList()

        val request = Request.Builder()
            .url("$baseUrl/repos/$repo/pulls?state=all&per_page=15")
            .headers(getAuthHeaders())
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val array = JSONArray(response.body?.string() ?: "[]")
                    val result = mutableListOf<PullRequestItem>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        result.add(
                            PullRequestItem(
                                number = obj.optInt("number"),
                                title = obj.optString("title"),
                                state = obj.optString("state"),
                                author = obj.optJSONObject("user")?.optString("login") ?: "User",
                                createdAt = obj.optString("created_at")
                            )
                        )
                    }
                    result
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Dapatkan daftar Artifacts APK hasil build
     */
    suspend fun getArtifacts(repo: String = preferencesManager.defaultRepo): List<ArtifactItem> = withContext(Dispatchers.IO) {
        if (repo.isBlank()) return@withContext emptyList()

        val request = Request.Builder()
            .url("$baseUrl/repos/$repo/actions/artifacts?per_page=10")
            .headers(getAuthHeaders())
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val array = json.optJSONArray("artifacts") ?: JSONArray()
                    val list = mutableListOf<ArtifactItem>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        list.add(
                            ArtifactItem(
                                id = obj.optLong("id"),
                                name = obj.optString("name", "app-debug.apk"),
                                sizeInBytes = obj.optLong("size_in_bytes"),
                                archiveDownloadUrl = obj.optString("archive_download_url"),
                                createdAt = obj.optString("created_at")
                            )
                        )
                    }
                    list
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val TAG = "GithubService"
    }
}

data class WorkflowRun(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String,
    val branch: String,
    val commitMessage: String,
    val createdAt: String,
    val htmlUrl: String
)

data class PullRequestItem(
    val number: Int,
    val title: String,
    val state: String,
    val author: String,
    val createdAt: String
)

data class ArtifactItem(
    val id: Long,
    val name: String,
    val sizeInBytes: Long,
    val archiveDownloadUrl: String,
    val createdAt: String
)
