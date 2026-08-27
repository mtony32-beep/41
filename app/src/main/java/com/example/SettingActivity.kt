package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.data.PreferencesManager
import com.example.service.AiService
import com.example.service.GithubService
import com.example.ui.theme.*
import com.example.util.Utils
import kotlinx.coroutines.launch

class SettingActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Rerev7Theme {
                SettingScreen(
                    onBack = { finish() },
                    activity = this
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    onBack: () -> Unit,
    activity: FragmentActivity
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesManager(context) }
    val githubService = remember { GithubService(context) }
    val aiService = remember { AiService(context) }

    var email by remember { mutableStateOf(prefs.githubEmail) }
    var name by remember { mutableStateOf(prefs.githubName) }
    var token by remember { mutableStateOf(prefs.githubToken) }
    var geminiKey by remember { mutableStateOf(prefs.geminiKey) }
    var repo by remember { mutableStateOf(prefs.defaultRepo) }

    var autoFixEnabled by remember { mutableStateOf(prefs.isAutoFixEnabled) }
    var autoBackupEnabled by remember { mutableStateOf(prefs.isAutoBackupEnabled) }

    var testGithubStatus by remember { mutableStateOf<String?>(null) }
    var testGeminiStatus by remember { mutableStateOf<String?>(null) }
    var isTestingGithub by remember { mutableStateOf(false) }
    var isTestingGemini by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan rerev7", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card Kredensial Terenkripsi (EncryptedSharedPreferences "config")
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kredensial Aman (EncryptedSharedPreferences)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CyberCyan)
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; prefs.githubEmail = it },
                        label = { Text("GITHUB_EMAIL") },
                        modifier = Modifier.fillMaxWidth().testTag("setting_email_input"),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; prefs.githubName = it },
                        label = { Text("GITHUB_NAME") },
                        modifier = Modifier.fillMaxWidth().testTag("setting_name_input"),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it; prefs.githubToken = it },
                        label = { Text("GITHUB_TOKEN (Personal Access Token)") },
                        modifier = Modifier.fillMaxWidth().testTag("setting_token_input"),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = geminiKey,
                        onValueChange = { geminiKey = it; prefs.geminiKey = it },
                        label = { Text("GEMINI_KEY (Google AI Studio Key)") },
                        modifier = Modifier.fillMaxWidth().testTag("setting_gemini_input"),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = repo,
                        onValueChange = { repo = it; prefs.defaultRepo = it },
                        label = { Text("DEFAULT_REPO (owner/repo_name)") },
                        modifier = Modifier.fillMaxWidth().testTag("setting_repo_input"),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }
            }

            // Card Diagnostic & Test API
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Uji Koneksi API", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CyberGreen)

                    // Test GitHub Button
                    Button(
                        onClick = {
                            scope.launch {
                                isTestingGithub = true
                                val (ok, msg) = githubService.testConnection()
                                testGithubStatus = msg
                                isTestingGithub = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isTestingGithub,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberPurple),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("test_github_button")
                    ) {
                        if (isTestingGithub) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test GitHub API", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (testGithubStatus != null) {
                        Text(testGithubStatus!!, fontSize = 11.sp, color = TextPrimary)
                    }

                    // Test Gemini Button
                    Button(
                        onClick = {
                            scope.launch {
                                isTestingGemini = true
                                val (ok, msg) = aiService.testConnection()
                                testGeminiStatus = msg
                                isTestingGemini = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isTestingGemini,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("test_gemini_button")
                    ) {
                        if (isTestingGemini) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Gemini API", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (testGeminiStatus != null) {
                        Text(testGeminiStatus!!, fontSize = 11.sp, color = TextPrimary)
                    }
                }
            }

            // Card Background Automation Toggles
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Otomasi Background Task", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CyberAmber)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Auto-Fix Service", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Pantau failure & auto perbaiki kode (Max 3x)", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = autoFixEnabled,
                            onCheckedChange = {
                                autoFixEnabled = it
                                prefs.isAutoFixEnabled = it
                            }
                        )
                    }

                    HorizontalDivider(color = DarkBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Auto-Backup (15 Menit)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Auto git commit saat ada perubahan file", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = autoBackupEnabled,
                            onCheckedChange = {
                                autoBackupEnabled = it
                                prefs.isAutoBackupEnabled = it
                            }
                        )
                    }
                }
            }

            // Card Backup & Restore (.aisuper)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup Terenkripsi (.aisuper)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CyberCyan)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val configStr = prefs.exportConfigString()
                                val encrypted = Utils.encryptText(configStr, "rerev7_secret_master_key")
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, encrypted)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Ekspor Backup rerev7"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberGreen, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("export_backup_button")
                        ) {
                            Text("Ekspor .aisuper", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                Utils.authenticateBiometric(
                                    activity = activity,
                                    title = "Konfirmasi Impor",
                                    subtitle = "Verifikasi biometrik sebelum mengganti konfigurasi",
                                    onSuccess = {
                                        Toast.makeText(context, "Kredensial aman terverifikasi", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("import_backup_button")
                        ) {
                            Text("Impor Backup", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
