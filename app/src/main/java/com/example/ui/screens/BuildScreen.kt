package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import com.example.data.entities.BuildQueueEntity
import com.example.service.ArtifactItem
import com.example.service.AutoFixWorker
import com.example.service.GithubService
import com.example.service.WorkflowRun
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildScreen(
    githubService: GithubService
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesManager(context) }
    val db = remember { AppDatabase.getInstance(context) }

    val pendingQueue by db.buildQueueDao().getPendingQueue().collectAsState(initial = emptyList())
    var artifacts by remember { mutableStateOf<List<ArtifactItem>>(emptyList()) }
    var latestBuilds by remember { mutableStateOf<List<WorkflowRun>>(emptyList()) }
    var isTriggering by remember { mutableStateOf(false) }

    fun refreshBuildData() {
        scope.launch {
            latestBuilds = githubService.getWorkflowRuns(prefs.defaultRepo)
            artifacts = githubService.getArtifacts(prefs.defaultRepo)
        }
    }

    LaunchedEffect(Unit) {
        refreshBuildData()
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = CyberCyan.copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Cloud Build Center", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("GitHub Actions CI/CD & Auto-Fix", fontSize = 11.sp, color = CyberGreen)
                        }
                    }

                    IconButton(onClick = { refreshBuildData() }, modifier = Modifier.testTag("build_refresh_button")) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Cloud Build Trigger Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Trigger GitHub Actions Build", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Workflow: build.yml (assembleDebug)", fontSize = 12.sp, color = TextMuted)
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (prefs.isAutoFixEnabled) CyberGreen.copy(alpha = 0.2f) else DarkBorder
                            ) {
                                Text(
                                    text = if (prefs.isAutoFixEnabled) "Auto-Fix ON" else "Auto-Fix OFF",
                                    color = if (prefs.isAutoFixEnabled) CyberGreen else TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    isTriggering = true
                                    val result = githubService.triggerCloudBuild()
                                    if (result.first) {
                                        Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
                                        refreshBuildData()
                                    } else {
                                        // Simpan ke antrian offline jika network error atau gagal
                                        db.buildQueueDao().insertQueue(
                                            BuildQueueEntity(
                                                repoName = prefs.defaultRepo,
                                                branch = "main",
                                                commitMessage = "Manual trigger cloud build"
                                            )
                                        )
                                        Toast.makeText(context, "Koneksi lambat, dimasukkan ke antrian build offline.", Toast.LENGTH_LONG).show()
                                    }
                                    isTriggering = false
                                }
                            },
                            enabled = !isTriggering,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("trigger_cloud_build_button")
                        ) {
                            if (isTriggering) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mentransmisikan ke GitHub...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mulai Cloud Build Sekarang", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // Auto-Fix Loop Status Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Loop, contentDescription = null, tint = CyberPurple, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Auto-Fix Status", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Loop berjalan: ${prefs.autoFixLoopCount} / 3", fontSize = 11.sp, color = TextMuted)
                            }
                        }

                        Button(
                            onClick = {
                                AutoFixWorker.runOnce(context)
                                Toast.makeText(context, "Memeriksa kegagalan build dengan Auto-Fix...", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPurple),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp).testTag("run_autofix_now_button")
                        ) {
                            Text("Cek Sekarang", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Offline Build Queue Section
            if (pendingQueue.isNotEmpty()) {
                item {
                    Text("Antrian Build Offline (${pendingQueue.size})", fontWeight = FontWeight.Bold, color = CyberAmber)
                }
                items(pendingQueue) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(item.repoName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Branch: ${item.branch} • Status: ${item.status}", fontSize = 11.sp, color = TextMuted)
                            }
                            IconButton(onClick = {
                                scope.launch { db.buildQueueDao().deleteQueue(item) }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = CyberRed)
                            }
                        }
                    }
                }
            }

            // Artifacts / Download APK Section
            item {
                Text("APK Hasil Build (Artifacts)", fontWeight = FontWeight.Bold, color = CyberCyan)
            }

            if (artifacts.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Android, contentDescription = null, tint = TextMuted, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Belum ada APK artifact tersedia", fontSize = 12.sp, color = TextMuted)
                        }
                    }
                }
            } else {
                items(artifacts) { artifact ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Android, contentDescription = null, tint = CyberGreen, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(artifact.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("${artifact.sizeInBytes / 1024 / 1024} MB • ${artifact.createdAt}", fontSize = 11.sp, color = TextMuted)
                                }
                            }

                            Button(
                                onClick = {
                                    Toast.makeText(context, "Mengunduh ${artifact.name}...", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberGreen, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp).testTag("download_apk_button")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
