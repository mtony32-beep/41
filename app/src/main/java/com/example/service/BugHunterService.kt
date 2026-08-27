package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.AppDatabase
import com.example.data.entities.BugScanEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * AI Bug Hunter Service (Feature 24):
 * Memindai file .kt proyek mencari pola berbahaya (!!, GlobalScope.launch, NetworkOnMainThread, findViewById tanpa ?, Log.e tanpa tag),
 * menyimpan ke Room DB, mengirim notifikasi, dan menyediakan perbaikan otomatis dengan AI.
 */
class BugHunterService(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val aiService = AiService(context)

    /**
     * Jalankan scan statis pada semua file .kt di direktori proyek
     */
    suspend fun scanProjectFiles(rootDir: File): List<BugScanEntity> = withContext(Dispatchers.IO) {
        val detectedBugs = mutableListOf<BugScanEntity>()

        if (!rootDir.exists() || !rootDir.isDirectory) {
            // Jika root dir belum dibuat di filesystem, scan sample workspace
            scanSourceCode("SampleFile.kt", SAMPLE_KT_CODE, detectedBugs)
        } else {
            scanDirectoryRecursive(rootDir, detectedBugs)
        }

        if (detectedBugs.isNotEmpty()) {
            db.bugScanDao().insertBugs(detectedBugs)
            sendBugNotification(detectedBugs.size)
        }

        detectedBugs
    }

    private fun scanDirectoryRecursive(dir: File, bugs: MutableList<BugScanEntity>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (file.name != ".git" && file.name != "build") {
                    scanDirectoryRecursive(file, bugs)
                }
            } else if (file.name.endsWith(".kt")) {
                try {
                    val content = file.readText()
                    scanSourceCode(file.absolutePath, content, bugs)
                } catch (e: Exception) {
                    Log.e(TAG, "Error membaca file ${file.name}", e)
                }
            }
        }
    }

    fun scanSourceCode(filePath: String, code: String, bugs: MutableList<BugScanEntity>) {
        val lines = code.lines()
        for ((index, line) in lines.withIndex()) {
            val lineNum = index + 1
            val trimmed = line.trim()

            // 1. Force unwrap (!!)
            if (trimmed.contains("!!") && !trimmed.startsWith("//")) {
                bugs.add(
                    BugScanEntity(
                        filePath = filePath,
                        lineNumber = lineNum,
                        patternType = "FORCE_UNWRAP (!!)",
                        codeSnippet = trimmed,
                        suggestedFix = "Ganti '!!' dengan safe call '?.' atau Elvis operator '?:'"
                    )
                )
            }

            // 2. GlobalScope.launch
            if (trimmed.contains("GlobalScope.launch") && !trimmed.startsWith("//")) {
                bugs.add(
                    BugScanEntity(
                        filePath = filePath,
                        lineNumber = lineNum,
                        patternType = "LEAK: GlobalScope.launch",
                        codeSnippet = trimmed,
                        suggestedFix = "Ganti dengan 'viewModelScope.launch' atau 'lifecycleScope.launch'"
                    )
                )
            }

            // 3. NetworkOnMainThread
            if (trimmed.contains("NetworkOnMainThread") || (trimmed.contains("HttpURLConnection") && !trimmed.contains("Dispatchers.IO"))) {
                bugs.add(
                    BugScanEntity(
                        filePath = filePath,
                        lineNumber = lineNum,
                        patternType = "BLOCKING: Network on Main Thread",
                        codeSnippet = trimmed,
                        suggestedFix = "Bungkus panggilan network di dalam 'withContext(Dispatchers.IO) { ... }'"
                    )
                )
            }

            // 4. findViewById tanpa ? / unsafe
            if (trimmed.contains("findViewById") && !trimmed.contains("?") && !trimmed.contains("viewBinding")) {
                bugs.add(
                    BugScanEntity(
                        filePath = filePath,
                        lineNumber = lineNum,
                        patternType = "NULL_CRASH: Unsafe findViewById",
                        codeSnippet = trimmed,
                        suggestedFix = "Gunakan ViewBinding atau Jetpack Compose deklaratif"
                    )
                )
            }

            // 5. Log.e tanpa tag jelas
            if (trimmed.contains("Log.e(\"\"") || trimmed.contains("Log.e(null")) {
                bugs.add(
                    BugScanEntity(
                        filePath = filePath,
                        lineNumber = lineNum,
                        patternType = "BAD_LOG: Log.e tanpa tag",
                        codeSnippet = trimmed,
                        suggestedFix = "Tambahkan konstanta TAG yang jelas pada parameter Log.e"
                    )
                )
            }
        }
    }

    /**
     * Perbaiki bug otomatis menggunakan AI
     */
    suspend fun fixBugWithAi(bug: BugScanEntity): String = withContext(Dispatchers.IO) {
        val prompt = """
            Perbaiki potensi crash/bug berikut:
            File: ${bug.filePath}
            Tipe Bug: ${bug.patternType}
            Baris Kode: ${bug.codeSnippet}
            Saran: ${bug.suggestedFix}

            Berikan potongan kode perbaikan yang aman dan bersih.
        """.trimIndent()

        val fixed = aiService.generateContent(prompt)
        db.bugScanDao().updateBug(bug.copy(isResolved = true))
        fixed
    }

    private fun sendBugNotification(count: Int) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "bug_hunter_channel"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Bug Hunter Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("AI Bug Hunter")
                .setContentText("Bug Hunter: Ditemukan $count potensi crash. Cek Tab Log")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(1001, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengirim notifikasi", e)
        }
    }

    companion object {
        private const val TAG = "BugHunterService"

        private val SAMPLE_KT_CODE = """
            class SampleRepository {
                fun fetchData(): String {
                    val user: String? = null
                    val length = user!!.length // Unsafe force unwrap
                    kotlinx.coroutines.GlobalScope.launch { // Leak global scope
                        println("Running...")
                    }
                    return "Result: " + length
                }
            }
        """.trimIndent()
    }
}
