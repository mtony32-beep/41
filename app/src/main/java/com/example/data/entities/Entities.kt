package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entitas Chat AI untuk menyimpan riwayat pesan chat di Room DB.
 */
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String, // "USER" atau "AI"
    val content: String,
    val attachedFile: String? = null,
    val attachedImageUri: String? = null,
    val generatedCode: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Entitas Proyek lokal yang dikelola di aplikasi.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val path: String,
    val templateType: String = "blank",
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * Entitas Log untuk menyimpan log build, logcat, atau tindakan error.
 */
@Entity(tableName = "action_logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val source: String, // "GITHUB_ACTIONS", "LOGCAT", "AUTO_FIX", "BUG_HUNTER"
    val logContent: String,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Entitas Antrian Build saat aplikasi offline.
 */
@Entity(tableName = "build_queue")
data class BuildQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val repoName: String,
    val branch: String,
    val commitMessage: String,
    val status: String = "PENDING", // PENDING, PROCESSING, COMPLETED, FAILED
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Entitas Ulasan Kode Otomatis dari Gemini AI (Feature 23).
 */
@Entity(tableName = "ai_reviews")
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val commitHash: String,
    val score: Int, // 1 - 10
    val summary: String,
    val recommendations: String,
    val potentialLeaks: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Entitas Hasil Scan AI Bug Hunter (Feature 24).
 */
@Entity(tableName = "bug_scans")
data class BugScanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val lineNumber: Int,
    val patternType: String, // "FORCE_UNWRAP", "GLOBAL_SCOPE", "NETWORK_ON_MAIN", "UNSAFE_FIND_VIEW", "UNTAGGED_LOG"
    val codeSnippet: String,
    val suggestedFix: String,
    val isResolved: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
