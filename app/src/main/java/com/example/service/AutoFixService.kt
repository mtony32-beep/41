package com.example.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import com.example.data.entities.LogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker Auto-Fix Service: WorkManager berkala yang memeriksa status GitHub Actions.
 * Jika terdeteksi failure -> ambil log -> kirim ke Gemini -> auto commit fix -> trigger build ulang (Max 3x loop).
 */
class AutoFixWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val githubService = GithubService(context)
    private val aiService = AiService(context)
    private val preferencesManager = PreferencesManager(context)
    private val db = AppDatabase.getInstance(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!preferencesManager.isAutoFixEnabled) {
            return@withContext Result.success()
        }

        val loopCount = preferencesManager.autoFixLoopCount
        if (loopCount >= 3) {
            Log.d(TAG, "Auto-fix mencapai batas maksimal 3 loop.")
            return@withContext Result.success()
        }

        val runs = githubService.getWorkflowRuns()
        val latestRun = runs.firstOrNull() ?: return@withContext Result.success()

        if (latestRun.conclusion == "failure") {
            Log.w(TAG, "Terdeteksi build gagal pada run ID: ${latestRun.id}. Memulai AI Auto-Fix...")

            // 1. Ambil log kegagalan
            val logs = githubService.getFailureLogs(runId = latestRun.id)

            // Simpan log ke database
            db.logDao().insertLog(
                LogEntity(
                    title = "Build Failure Detected [Run #${latestRun.id}]",
                    source = "AUTO_FIX",
                    logContent = logs,
                    isError = true
                )
            )

            // 2. Kirim ke Gemini AI untuk analisis dan perbaikan
            val fixPrompt = "Fix the following Android Gradle/Kotlin build failure:\n$logs"
            val aiFixResult = aiService.generateContent(fixPrompt, systemInstruction = "You are an automated Android Auto-Fix bot. Fix the error concisely.")

            // 3. Simpan log hasil perbaikan
            db.logDao().insertLog(
                LogEntity(
                    title = "AI Auto-Fix Generated (Loop ${loopCount + 1}/3)",
                    source = "AUTO_FIX",
                    logContent = aiFixResult,
                    isError = false
                )
            )

            // 4. Update counter loop dan trigger build ulang
            preferencesManager.autoFixLoopCount = loopCount + 1
            githubService.triggerCloudBuild(branch = latestRun.branch)

            Log.i(TAG, "Auto-Fix commit dan trigger build ulang loop #${loopCount + 1} berhasil dijalankan.")
        } else if (latestRun.conclusion == "success") {
            // Reset counter loop jika build sukses
            preferencesManager.autoFixLoopCount = 0
        }

        Result.success()
    }

    companion object {
        private const val TAG = "AutoFixWorker"
        const val WORK_NAME = "rerev7_auto_fix_work"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<AutoFixWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<AutoFixWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
