package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.ProjectManager
import com.example.ui.theme.*
import kotlinx.coroutines.launch

data class LibraryItem(
    val name: String,
    val description: String,
    val dependencyNotation: String
)

@Composable
fun LibraryManagerDialog(
    projectManager: ProjectManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val popularLibraries = remember {
        listOf(
            LibraryItem("Retrofit REST Client", "HTTP client type-safe untuk koneksi API", "com.squareup.retrofit2:retrofit:2.9.0"),
            LibraryItem("Retrofit Gson Converter", "Parser JSON otomatis untuk Retrofit", "com.squareup.retrofit2:converter-gson:2.9.0"),
            LibraryItem("OkHttp Logging Interceptor", "Pencatat request/response HTTP di Logcat", "com.squareup.okhttp3:logging-interceptor:4.12.0"),
            LibraryItem("Coil Compose Image Loading", "Pemuat gambar asinkron cepat & ringan untuk Compose", "io.coil-kt:coil-compose:2.6.0"),
            LibraryItem("Room Database KTX", "Abstraksi SQLite modern dengan Flow & Coroutines", "androidx.room:room-ktx:2.6.1"),
            LibraryItem("Navigation Compose", "Sistem navigasi antar screen type-safe Compose", "androidx.navigation:navigation-compose:2.8.0"),
            LibraryItem("Lifecycle ViewModel Compose", "Integrasi ViewModel & StateFlow di Compose", "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0"),
            LibraryItem("WorkManager KTX", "Penjadwalan background task berkala yang andal", "androidx.work:work-runtime-ktx:2.9.0"),
            LibraryItem("Security Crypto", "EncryptedSharedPreferences & MasterKey AES-256", "androidx.security:security-crypto:1.1.0-alpha06"),
            LibraryItem("Biometric Prompt", "Autentikasi Sidik Jari / Wajah Biometrik", "androidx.biometric:biometric:1.2.0-alpha05"),
            LibraryItem("Material Icons Extended", "Koleksi ribuan icon Material Design lengkap", "androidx.compose.material:material-icons-extended:1.6.8"),
            LibraryItem("Kotlinx Coroutines Android", "Concurrency & asynchronous programming", "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0"),
            LibraryItem("Kotlinx Serialization JSON", "Serialisasi JSON resmi dari Kotlin", "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3"),
            LibraryItem("CameraX Camera2", "Engine kamera performa tinggi CameraX", "androidx.camera:camera-camera2:1.3.3"),
            LibraryItem("CameraX Lifecycle", "Integrasi siklus hidup kamera Android", "androidx.camera:camera-lifecycle:1.3.3"),
            LibraryItem("CameraX View PreviewView", "Tampilan live preview kamera", "androidx.camera:camera-view:1.3.3"),
            LibraryItem("Accompanist Permissions", "Penangan izin runtime interaktif di Compose", "com.google.accompanist:accompanist-permissions:0.34.0"),
            LibraryItem("DataStore Preferences", "Pengganti SharedPreferences modern asinkron", "androidx.datastore:datastore-preferences:1.1.1"),
            LibraryItem("Lottie Compose", "Pemutar animasi vektor After Effects JSON", "com.airbnb.android:lottie-compose:6.4.0"),
            LibraryItem("Core SplashScreen", "API splash screen modern Android 12+", "androidx.core:core-splashscreen:1.0.1")
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Extension, contentDescription = null, tint = CyberCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Library Manager (1-Click Install)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(popularLibraries) { lib ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(lib.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CyberCyan)
                                Text(lib.description, fontSize = 11.sp, color = TextMuted)
                                Text(lib.dependencyNotation, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextSecondary, maxLines = 1)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val result = projectManager.addLibraryToGradle(lib.dependencyNotation)
                                        Toast.makeText(context, result.second, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.testTag("add_lib_${lib.name.replace(" ", "_")}")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = CyberGreen)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black)
            ) {
                Text("Selesai", fontWeight = FontWeight.Bold)
            }
        }
    )
}
