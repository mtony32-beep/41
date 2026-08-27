package com.example.service

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.entities.ProjectEntity
import com.example.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Manajer Proyek Lokal di HP:
 * Membaca struktur file proyek, membuat file baru, menyimpan kode, mengelola template.
 */
class ProjectManager(private val context: Context) {

    private val db = AppDatabase.getInstance(context)

    val workspaceRoot: File
        get() {
            val dir = File(context.filesDir, "AIStudioWorkspace")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    /**
     * Inisialisasi workspace awal jika masih kosong
     */
    suspend fun ensureDefaultWorkspace() = withContext(Dispatchers.IO) {
        val root = workspaceRoot
        val mainKt = File(root, "app/src/main/java/com/example/MainActivity.kt")
        if (!mainKt.exists()) {
            mainKt.parentFile?.mkdirs()
            mainKt.writeText(DEFAULT_MAIN_ACTIVITY)
        }

        val buildGradle = File(root, "app/build.gradle.kts")
        if (!buildGradle.exists()) {
            buildGradle.parentFile?.mkdirs()
            buildGradle.writeText(DEFAULT_BUILD_GRADLE)
        }

        val manifest = File(root, "app/src/main/AndroidManifest.xml")
        if (!manifest.exists()) {
            manifest.parentFile?.mkdirs()
            manifest.writeText(DEFAULT_MANIFEST)
        }

        db.projectDao().insertProject(
            ProjectEntity(
                id = "rerev7_workspace",
                name = "rerev7 Main Workspace",
                path = root.absolutePath,
                templateType = "blank"
            )
        )
    }

    /**
     * Dapatkan daftar semua file kode dalam workspace
     */
    suspend fun getAllProjectFiles(): List<ProjectFileItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ProjectFileItem>()
        scanDir(workspaceRoot, workspaceRoot, list)
        if (list.isEmpty()) {
            ensureDefaultWorkspace()
            scanDir(workspaceRoot, workspaceRoot, list)
        }
        list
    }

    private fun scanDir(root: File, current: File, list: MutableList<ProjectFileItem>) {
        val files = current.listFiles() ?: return
        for (file in files) {
            if (file.name == ".git" || file.name == "build") continue
            if (file.isDirectory) {
                scanDir(root, file, list)
            } else {
                val relative = file.relativeTo(root).path
                val ext = file.extension
                list.add(
                    ProjectFileItem(
                        name = file.name,
                        relativePath = relative,
                        absolutePath = file.absolutePath,
                        extension = ext,
                        size = file.length()
                    )
                )
            }
        }
    }

    /**
     * Baca isi file
     */
    suspend fun readFile(relativePath: String): String = withContext(Dispatchers.IO) {
        val file = File(workspaceRoot, relativePath)
        if (file.exists()) {
            file.readText()
        } else {
            "// File $relativePath belum dibuat."
        }
    }

    /**
     * Simpan teks kode ke file
     */
    suspend fun saveFile(relativePath: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(workspaceRoot, relativePath)
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyimpan file $relativePath", e)
            false
        }
    }

    /**
     * Buat proyek baru dari template (blank, camera-app, api-app)
     */
    suspend fun createProjectFromTemplate(templateId: String, projectName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val projectDir = File(workspaceRoot, projectName.replace("\\s+".toRegex(), "_"))
            projectDir.mkdirs()

            when (templateId) {
                "camera-app" -> {
                    val main = File(projectDir, "app/src/main/java/com/example/MainActivity.kt")
                    main.parentFile?.mkdirs()
                    main.writeText(CAMERA_TEMPLATE_CODE)
                }
                "api-app" -> {
                    val main = File(projectDir, "app/src/main/java/com/example/MainActivity.kt")
                    main.parentFile?.mkdirs()
                    main.writeText(API_TEMPLATE_CODE)
                }
                else -> {
                    val main = File(projectDir, "app/src/main/java/com/example/MainActivity.kt")
                    main.parentFile?.mkdirs()
                    main.writeText(DEFAULT_MAIN_ACTIVITY)
                }
            }

            db.projectDao().insertProject(
                ProjectEntity(
                    id = "proj_${System.currentTimeMillis()}",
                    name = projectName,
                    path = projectDir.absolutePath,
                    templateType = templateId
                )
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membuat proyek dari template $templateId", e)
            false
        }
    }

    /**
     * Tambahkan library ke file build.gradle.kts secara otomatis (Feature 15: Library Manager)
     */
    suspend fun addLibraryToGradle(dependencyNotation: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val gradleFile = File(workspaceRoot, "app/build.gradle.kts")
        if (!gradleFile.exists()) {
            return@withContext Pair(false, "File app/build.gradle.kts tidak ditemukan.")
        }

        try {
            val content = gradleFile.readText()
            if (content.contains(dependencyNotation)) {
                return@withContext Pair(true, "Library sudah ada di build.gradle.kts.")
            }

            val updatedContent = if (content.contains("dependencies {")) {
                content.replaceFirst("dependencies {", "dependencies {\n    implementation(\"$dependencyNotation\")")
            } else {
                content + "\ndependencies {\n    implementation(\"$dependencyNotation\")\n}\n"
            }

            gradleFile.writeText(updatedContent)
            Pair(true, "Berhasil menambahkan $dependencyNotation ke build.gradle.kts!")
        } catch (e: Exception) {
            Pair(false, "Error: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ProjectManager"

        private val DEFAULT_MAIN_ACTIVITY = """
            package com.example

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        MaterialTheme {
                            Surface(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Halo dari rerev7 Mobile IDE!",
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        private val DEFAULT_BUILD_GRADLE = """
            plugins {
                alias(libs.plugins.android.application)
                alias(libs.plugins.kotlin.compose)
            }

            android {
                namespace = "com.example"
                compileSdk = 34
                defaultConfig {
                    applicationId = "com.example.myapp"
                    minSdk = 24
                    targetSdk = 34
                    versionCode = 1
                    versionName = "1.0"
                }
            }

            dependencies {
                implementation(libs.androidx.compose.material3)
                implementation(libs.androidx.activity.compose)
            }
        """.trimIndent()

        private val DEFAULT_MANIFEST = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application
                    android:allowBackup="true"
                    android:icon="@mipmap/ic_launcher"
                    android:label="rerev7 Project"
                    android:theme="@android:style/Theme.Material.NoActionBar">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
        """.trimIndent()

        private val CAMERA_TEMPLATE_CODE = """
            package com.example

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.foundation.layout.*
            import androidx.compose.material.icons.Icons
            import androidx.compose.material.icons.filled.CameraAlt
            import androidx.compose.material3.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        MaterialTheme {
                            Scaffold(
                                floatingActionButton = {
                                    FloatingActionButton(onClick = { /* Shutter */ }) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = "Foto")
                                    }
                                }
                            ) { padding ->
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(padding),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("CameraX Preview Window")
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        private val API_TEMPLATE_CODE = """
            package com.example

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        MaterialTheme {
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                Text("REST API Client Template", style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { /* Trigger Retrofit Request */ }) {
                                    Text("Fetch Data API")
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
    }
}

data class ProjectFileItem(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val extension: String,
    val size: Long
)
