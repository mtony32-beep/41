package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.data.entities.BugScanEntity
import com.example.data.entities.ChatMessage
import com.example.data.entities.LogEntity
import com.example.data.entities.ReviewEntity
import com.example.service.AiService
import com.example.service.BugHunterService
import com.example.service.ProjectManager
import com.example.ui.theme.*
import com.example.util.Utils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    aiService: AiService,
    projectManager: ProjectManager,
    onNavigateToChat: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val bugHunter = remember { BugHunterService(context) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Build & Action Logs, 1: AI Code Review, 2: AI Bug Hunter

    val logs by db.logDao().getAllLogs().collectAsState(initial = emptyList())
    val reviews by db.reviewDao().getAllReviews().collectAsState(initial = emptyList())
    val bugs by db.bugScanDao().getActiveBugs().collectAsState(initial = emptyList())

    var isScanningBugs by remember { mutableStateOf(false) }
    var isRunningReview by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                    Icon(Icons.Default.BugReport, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Log & Diagnostics", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Actions Logs • Bug Hunter • AI Review", fontSize = 11.sp, color = CyberGreen)
                            }
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    db.logDao().clearLogs()
                                    Toast.makeText(context, "Log berhasil dibersihkan", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("clear_logs_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = CyberCyan
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Logs (${logs.size})", fontSize = 12.sp) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("AI Review", fontSize = 12.sp) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Bug Hunter (${bugs.size})", fontSize = 12.sp, color = if (bugs.isNotEmpty()) CyberRed else CyberCyan) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> {
                    // TAB 0: BUILD & ACTION LOGS
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (logs.isEmpty()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.Notes, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Belum ada catatan log.", fontSize = 13.sp, color = TextMuted)
                                    }
                                }
                            }
                        }

                        items(logs, key = { it.id }) { log ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (log.isError) CyberRed.copy(alpha = 0.1f) else DarkCardBg
                                ),
                                border = if (log.isError) androidx.compose.foundation.BorderStroke(1.dp, CyberRed.copy(alpha = 0.5f)) else null,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = log.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (log.isError) CyberRed else TextPrimary
                                        )
                                        Text(
                                            text = Utils.formatDateTime(log.timestamp),
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Surface(
                                        color = EditorGutterBg,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = log.logContent,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = if (log.isError) CyberRed else TerminalGreen,
                                            modifier = Modifier.padding(8.dp),
                                            maxLines = 6
                                        )
                                    }

                                    // Button "Tanya AI tentang error ini"
                                    if (log.isError) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    db.chatDao().insertMessage(
                                                        ChatMessage(
                                                            sender = "USER",
                                                            content = "Tolong analisa dan jelaskan solusi untuk error log berikut:\n${log.logContent}"
                                                        )
                                                    )
                                                    onNavigateToChat()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(30.dp).testTag("ask_ai_error_button")
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Tanya AI tentang error ini", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: AI CODE REVIEW (Feature 23)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("AI Code Reviewer (Senior Android Dev)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CyberCyan)
                                    Text("Evaluasi kualitas arsitektur, 3 saran perbaikan, deteksi memory leak, dan skor 1-10.", fontSize = 12.sp, color = TextMuted)
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isRunningReview = true
                                                val mainCode = projectManager.readFile("app/src/main/java/com/example/MainActivity.kt")
                                                val (score, summary) = aiService.reviewCodeDiff(mainCode)
                                                db.reviewDao().insertReview(
                                                    ReviewEntity(
                                                        commitHash = "main-HEAD",
                                                        score = score,
                                                        summary = summary,
                                                        recommendations = "Gunakan Coroutine ViewModelScope, tambahkan M3 dynamic color",
                                                        potentialLeaks = "Periksa lifecycle-aware subscription pada Flow"
                                                    )
                                                )
                                                isRunningReview = false
                                                Toast.makeText(context, "Review selesai! Skor: $score/10", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        enabled = !isRunningReview,
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberPurple),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("run_ai_review_button")
                                    ) {
                                        if (isRunningReview) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                                        } else {
                                            Icon(Icons.Default.RateReview, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Jalankan AI Code Review", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        items(reviews, key = { it.id }) { review ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Score Code Quality", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = when {
                                                review.score >= 8 -> CyberGreen.copy(alpha = 0.2f)
                                                review.score >= 6 -> CyberAmber.copy(alpha = 0.2f)
                                                else -> CyberRed.copy(alpha = 0.2f)
                                            }
                                        ) {
                                            Text(
                                                text = "${review.score} / 10",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = if (review.score >= 8) CyberGreen else if (review.score >= 6) CyberAmber else CyberRed,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = review.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 2: AI BUG HUNTER (Feature 24)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("AI Bug Hunter (Static Scanner)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CyberRed)
                                    Text("Pindai pola berbahaya: '!!', GlobalScope, Network on Main Thread, unsafe findViewById, bad logging.", fontSize = 12.sp, color = TextMuted)
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isScanningBugs = true
                                                val found = bugHunter.scanProjectFiles(projectManager.workspaceRoot)
                                                isScanningBugs = false
                                                Toast.makeText(context, "Scan selesai: Ditemukan ${found.size} potensi crash!", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        enabled = !isScanningBugs,
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberRed),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("scan_bugs_button")
                                    ) {
                                        if (isScanningBugs) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                                        } else {
                                            Icon(Icons.Default.Security, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Pindai File Proyek Sekarang", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        if (bugs.isEmpty()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CyberGreen, modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Workspace Bersih & Aman!", fontWeight = FontWeight.Bold, color = CyberGreen)
                                        Text("Tidak ditemukan pola berbahaya atau potensi crash.", fontSize = 12.sp, color = TextMuted)
                                    }
                                }
                            }
                        }

                        items(bugs, key = { it.id }) { bug ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CyberRed.copy(alpha = 0.08f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CyberRed.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(bug.patternType, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CyberRed)
                                        Text("Baris ${bug.lineNumber}", fontSize = 11.sp, color = TextMuted)
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Surface(
                                        color = EditorGutterBg,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = bug.codeSnippet,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = TextPrimary,
                                            modifier = Modifier.padding(6.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = "Saran: ${bug.suggestedFix}", fontSize = 11.sp, color = CyberAmber)

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Button "Perbaiki Otomatis"
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val fixed = bugHunter.fixBugWithAi(bug)
                                                Toast.makeText(context, "Bug diperbaiki!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp).testTag("fix_bug_button")
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Perbaiki Otomatis", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
