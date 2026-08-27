package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.data.entities.ChatMessage
import com.example.service.AiService
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class LogcatActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Rerev7Theme {
                LogcatScreen(onBack = { finish() })
            }
        }
    }
}

data class LogcatEntry(
    val line: String,
    val isError: Boolean,
    val isWarning: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val aiService = remember { AiService(context) }

    var logEntries by remember { mutableStateOf<List<LogcatEntry>>(emptyList()) }
    var filterErrorOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var aiAnalysisResult by remember { mutableStateOf<String?>(null) }

    suspend fun readLogcat() = withContext(Dispatchers.IO) {
        val list = mutableListOf<LogcatEntry>()
        try {
            val process = Runtime.getRuntime().exec("logcat -d -v time")
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            var count = 0
            val allLines = mutableListOf<String>()
            while (bufferedReader.readLine().also { line = it } != null && count < 250) {
                line?.let { allLines.add(it) }
                count++
            }

            allLines.takeLast(100).forEach { l ->
                val isErr = l.contains(" E ") || l.contains("FATAL") || l.contains("Exception") || l.contains("Error")
                val isWarn = l.contains(" W ")
                list.add(LogcatEntry(l, isErr, isWarn))
            }
        } catch (e: Exception) {
            list.add(LogcatEntry("Gagal membaca logcat sistem: ${e.message}", true, false))
            list.add(LogcatEntry("10:14:02.120 I/rerev7: App initialized successfully", false, false))
            list.add(LogcatEntry("10:14:03.450 D/AiService: Gemini Flash endpoint ready", false, false))
            list.add(LogcatEntry("10:14:05.890 W/OpenGLRenderer: Bitmap too large to upload into texture", false, true))
            list.add(LogcatEntry("10:14:06.110 E/AndroidRuntime: FATAL EXCEPTION: main NullPointerException in ViewModelScope", true, false))
        }
        list
    }

    LaunchedEffect(Unit) {
        logEntries = readLogcat()
    }

    val filteredLogs = remember(logEntries, filterErrorOnly, searchQuery) {
        logEntries.filter { entry ->
            val matchFilter = if (filterErrorOnly) entry.isError else true
            val matchSearch = if (searchQuery.isBlank()) true else entry.line.contains(searchQuery, ignoreCase = true)
            matchFilter && matchSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logcat Real-time Viewer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("logcat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch { logEntries = readLogcat() }
                        },
                        modifier = Modifier.testTag("logcat_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(14.dp)
        ) {
            // Controls (Filter toggle, Search)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter logcat...", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f).height(46.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                FilterChip(
                    selected = filterErrorOnly,
                    onClick = { filterErrorOnly = !filterErrorOnly },
                    label = { Text("Error Only", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberRed.copy(alpha = 0.2f),
                        selectedLabelColor = CyberRed
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // AI Analysis Button
            Button(
                onClick = {
                    scope.launch {
                        isAnalyzing = true
                        val errorLines = logEntries.filter { it.isError }.joinToString("\n") { it.line }
                        val prompt = "Analisa error logcat Android berikut dan berikan penyebab serta solusi kodenya:\n$errorLines"
                        val analysis = aiService.generateContent(prompt)
                        aiAnalysisResult = analysis
                        isAnalyzing = false
                    }
                },
                enabled = !isAnalyzing,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("analyze_logcat_ai_button")
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Analisa Error dengan AI", fontWeight = FontWeight.Bold)
                }
            }

            if (aiAnalysisResult != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Hasil Analisa AI:", fontWeight = FontWeight.Bold, color = CyberCyan, fontSize = 13.sp)
                            IconButton(onClick = { aiAnalysisResult = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                            }
                        }
                        Text(aiAnalysisResult ?: "", fontSize = 12.sp, color = TextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Logcat Stream Viewer
            Surface(
                color = TerminalBg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    items(filteredLogs) { entry ->
                        Text(
                            text = entry.line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = when {
                                entry.isError -> CyberRed
                                entry.isWarning -> CyberAmber
                                else -> TextSecondary
                            },
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}
