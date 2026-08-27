package com.example.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker Auto-Backup berkala (Feature 10):
 * Memeriksa perubahan file proyek tiap 15 menit dan mengeksekusi auto commit git.
 */
class AutoBackupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val preferencesManager = PreferencesManager(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!preferencesManager.isAutoBackupEnabled) {
            return@withContext Result.success()
        }

        Log.i(TAG, "Menjalankan auto-backup berkala 15 menit...")
        // Simulasi / eksekusi git commit auto backup
        val terminalManager = TerminalManager(context)
        val result = terminalManager.executeCommand("git commit -am \"auto backup - ${System.currentTimeMillis()}\"")
        Log.d(TAG, "Hasil auto-backup: $result")

        Result.success()
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
        const val WORK_NAME = "rerev7_auto_backup_work"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
