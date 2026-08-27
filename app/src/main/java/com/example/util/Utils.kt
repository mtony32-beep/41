package com.example.util

import android.content.Context
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Utils {

    /**
     * Format timestamp menjadi format tanggal dan waktu yang mudah dibaca
     */
    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * Memeriksa dan menjalankan autentikasi biometrik (Fingerprint / Face ID)
     */
    fun authenticateBiometric(
        activity: FragmentActivity,
        title: String = "Autentikasi rerev7",
        subtitle: String = "Gunakan sidik jari atau biometrik untuk melanjutkan",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val biometricManager = BiometricManager.from(activity)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            // Jika perangkat tidak memiliki biometrik atau belum dikonfigurasi, langsung loloskan
            onSuccess()
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onError("Autentikasi dibatalkan")
                } else {
                    onError("Error biometrik: $errString")
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Biometrik tidak cocok, coba lagi")
            }
        })

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Enkripsi sederhana untuk backup file .aisuper
     */
    fun encryptText(plainText: String, secretKey: String): String {
        return try {
            val keyBytes = MessageDigest.getInstance("SHA-256").digest(secretKey.toByteArray(StandardCharsets.UTF_8))
            val plainBytes = plainText.toByteArray(StandardCharsets.UTF_8)
            val encryptedBytes = ByteArray(plainBytes.size)
            for (i in plainBytes.indices) {
                encryptedBytes[i] = (plainBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText
        }
    }

    /**
     * Dekripsi file backup .aisuper
     */
    fun decryptText(encryptedBase64: String, secretKey: String): String {
        return try {
            val keyBytes = MessageDigest.getInstance("SHA-256").digest(secretKey.toByteArray(StandardCharsets.UTF_8))
            val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            val decryptedBytes = ByteArray(encryptedBytes.size)
            for (i in encryptedBytes.indices) {
                decryptedBytes[i] = (encryptedBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            encryptedBase64
        }
    }

    /**
     * Kompres direktori proyek ke file ZIP untuk diupload ke Cloud Build
     */
    fun zipDirectory(sourceDir: File, zipFile: File): Boolean {
        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                zipFileSub(sourceDir, sourceDir, zos)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun zipFileSub(rootDir: File, currentFile: File, zos: ZipOutputStream) {
        val files = currentFile.listFiles() ?: return
        val buffer = ByteArray(2048)
        for (file in files) {
            if (file.name == ".git" || file.name == "build" || file.name == ".gradle") {
                continue
            }
            if (file.isDirectory) {
                zipFileSub(rootDir, file, zos)
            } else {
                val relativePath = file.relativeTo(rootDir).path
                val entry = ZipEntry(relativePath)
                zos.putNextEntry(entry)
                FileInputStream(file).use { fis ->
                    var count: Int
                    while (fis.read(buffer).also { count = it } != -1) {
                        zos.write(buffer, 0, count)
                    }
                }
                zos.closeEntry()
            }
        }
    }

    /**
     * Syntax highlighter untuk teks kode di Sora-Editor / Code View Jetpack Compose
     */
    fun highlightCode(code: String, fileExtension: String): AnnotatedString {
        val keywords = when (fileExtension.lowercase()) {
            "kt", "kts" -> listOf(
                "package", "import", "class", "interface", "object", "val", "var", "fun",
                "if", "else", "when", "for", "while", "return", "private", "public",
                "protected", "internal", "data", "sealed", "override", "abstract",
                "suspend", "companion", "by", "is", "as", "true", "false", "null", "typealias"
            )
            "java" -> listOf(
                "package", "import", "public", "private", "protected", "class", "interface",
                "extends", "implements", "void", "int", "boolean", "String", "return",
                "if", "else", "for", "while", "switch", "case", "break", "new", "this",
                "super", "static", "final", "try", "catch", "finally", "throw", "throws"
            )
            "xml" -> listOf(
                "xmlns", "android", "app", "tools", "version", "encoding"
            )
            "gradle", "gradle.kts" -> listOf(
                "plugins", "alias", "android", "defaultConfig", "buildTypes", "dependencies",
                "implementation", "testImplementation", "ksp", "compileSdk", "minSdk", "targetSdk"
            )
            else -> listOf("val", "var", "fun", "class", "import", "package")
        }

        val keywordColor = Color(0xFFC792EA) // Purple
        val stringColor = Color(0xFFC3E88D)  // Green
        val commentColor = Color(0xFF7585A2) // Gray
        val annotationColor = Color(0xFFFFCB6B) // Gold
        val numberColor = Color(0xFFF78C6C)  // Orange
        val typeColor = Color(0xFF82AAFF)    // Cyan/Blue

        return buildAnnotatedString {
            append(code)

            // Highlight Comments (// ...)
            val commentPattern = Pattern.compile("//.*|/\\*(.|[\\r\\n])*?\\*/")
            val commentMatcher = commentPattern.matcher(code)
            while (commentMatcher.find()) {
                addStyle(
                    SpanStyle(color = commentColor, fontWeight = FontWeight.Normal),
                    commentMatcher.start(),
                    commentMatcher.end()
                )
            }

            // Highlight Strings ("...")
            val stringPattern = Pattern.compile("\".*?\"")
            val stringMatcher = stringPattern.matcher(code)
            while (stringMatcher.find()) {
                addStyle(
                    SpanStyle(color = stringColor),
                    stringMatcher.start(),
                    stringMatcher.end()
                )
            }

            // Highlight Annotations (@...)
            val annotationPattern = Pattern.compile("@[A-Za-z0-9_]+")
            val annotationMatcher = annotationPattern.matcher(code)
            while (annotationMatcher.find()) {
                addStyle(
                    SpanStyle(color = annotationColor, fontWeight = FontWeight.SemiBold),
                    annotationMatcher.start(),
                    annotationMatcher.end()
                )
            }

            // Highlight Numbers
            val numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?f?\\b")
            val numberMatcher = numberPattern.matcher(code)
            while (numberMatcher.find()) {
                addStyle(
                    SpanStyle(color = numberColor),
                    numberMatcher.start(),
                    numberMatcher.end()
                )
            }

            // Highlight Keywords
            for (keyword in keywords) {
                val pattern = Pattern.compile("\\b$keyword\\b")
                val matcher = pattern.matcher(code)
                while (matcher.find()) {
                    addStyle(
                        SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
                        matcher.start(),
                        matcher.end()
                    )
                }
            }
        }
    }
}
