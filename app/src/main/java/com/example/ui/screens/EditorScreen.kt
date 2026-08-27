package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.data.entities.LogEntity
import com.example.service.AiService
import com.example.service.ProjectFileItem
import com.example.service.ProjectManager
import com.example.ui.theme.*
import com.example.util.Utils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    aiService: AiService,
    projectManager: ProjectManager,
    initialFilePath: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }

    var projectFiles by remember { mutableStateOf<List<ProjectFileItem>>(emptyList()) }
    var activeFile by remember { mutableStateOf<ProjectFileItem?>(null) }
    var codeContent by remember { mutableStateOf("") }
    var isDirty by remember { mutableStateOf(false) }

    // Undo / Redo history
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }

    // Search bar state
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // AI Code Completion suggestion
    var aiSuggestion by remember { mutableStateOf<String?>(null) }
    var isGeneratingCompletion by remember { mutableStateOf(false) }

    // Generate Test modal state
    var isTestGenerating by remember { mutableStateOf(false) }
    var generatedTestResult by remember { mutableStateOf<String?>(null) }
    var showTestDialog by remember { mutableStateOf(false) }

    // Load project files
    LaunchedEffect(Unit) {
        projectFiles = projectManager.getAllProjectFiles()
        val target = if (initialFilePath != null) {
            projectFiles.find { it.relativePath == initialFilePath }
        } else {
            projectFiles.firstOrNull()
        } ?: projectFiles.firstOrNull()

        if (target != null) {
            activeFile = target
            codeContent = projectManager.readFile(target.relativePath)
        }
    }

    // Auto-save effect (saves 2 seconds after typing stops)
    LaunchedEffect(codeContent) {
        if (isDirty && activeFile != null) {
            delay(2000)
            activeFile?.let {
                projectManager.saveFile(it.relativePath, codeContent)
                isDirty = false
            }
        }
    }

    val lines = remember(codeContent) { codeContent.lines() }
    val extension = activeFile?.extension ?: "kt"

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // File tabs row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        projectFiles.forEach { file ->
                            val isSelected = activeFile?.relativePath == file.relativePath
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isDirty && activeFile != null) {
                                        scope.launch {
                                            projectManager.saveFile(activeFile!!.relativePath, codeContent)
                                        }
                                    }
                                    activeFile = file
                                    scope.launch {
                                        codeContent = projectManager.readFile(file.relativePath)
                                        undoStack.clear()
                                        redoStack.clear()
                                        isDirty = false
                                        aiSuggestion = null
                                    }
                                },
                                label = {
                                    Text(
                                        text = file.name + if (isSelected && isDirty) " *" else "",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = CyberCyan
                                )
                            )
                        }
                    }

                    // Action toolbar (Save, Undo, Redo, Search, AI Complete, Generate Test)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Undo
                            IconButton(
                                onClick = {
                                    if (undoStack.isNotEmpty()) {
                                        redoStack.add(codeContent)
                                        val previous = undoStack.removeAt(undoStack.size - 1)
                                        codeContent = previous
                                    }
                                },
                                enabled = undoStack.isNotEmpty(),
                                modifier = Modifier.size(36.dp).testTag("editor_undo_button")
                            ) {
                                Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (undoStack.isNotEmpty()) CyberCyan else TextMuted)
                            }

                            // Redo
                            IconButton(
                                onClick = {
                                    if (redoStack.isNotEmpty()) {
                                        undoStack.add(codeContent)
                                        val next = redoStack.removeAt(redoStack.size - 1)
                                        codeContent = next
                                    }
                                },
                                enabled = redoStack.isNotEmpty(),
                                modifier = Modifier.size(36.dp).testTag("editor_redo_button")
                            ) {
                                Icon(Icons.Default.Redo, contentDescription = "Redo", tint = if (redoStack.isNotEmpty()) CyberCyan else TextMuted)
                            }

                            // Save
                            IconButton(
                                onClick = {
                                    activeFile?.let {
                                        scope.launch {
                                            projectManager.saveFile(it.relativePath, codeContent)
                                            isDirty = false
                                            Toast.makeText(context, "File ${it.name} berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.size(36.dp).testTag("editor_save_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Save", tint = if (isDirty) CyberAmber else CyberGreen)
                            }

                            // Search
                            IconButton(
                                onClick = { isSearchOpen = !isSearchOpen },
                                modifier = Modifier.size(36.dp).testTag("editor_search_button")
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = CyberCyan)
                            }
                        }

                        // AI Actions
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // AI Code Completion Button
                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        isGeneratingCompletion = true
                                        val last5Lines = lines.takeLast(5).joinToString("\n")
                                        val suggestion = aiService.getCodeCompletion(last5Lines, extension)
                                        aiSuggestion = suggestion
                                        isGeneratingCompletion = false
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp).testTag("editor_ai_completion_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = CyberCyan)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("AI Auto", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Generate Test Button
                            Button(
                                onClick = {
                                    val file = activeFile ?: return@Button
                                    scope.launch {
                                        isTestGenerating = true
                                        showTestDialog = true
                                        val testCode = aiService.generateTestsForFile(file.name, codeContent)
                                        generatedTestResult = testCode

                                        // Simpan otomatis ke /app/src/test/
                                        val testFileName = file.name.replace(".kt", "Test.kt")
                                        val testPath = "app/src/test/java/com/example/$testFileName"
                                        projectManager.saveFile(testPath, testCode)

                                        // Simpan ke log
                                        db.logDao().insertLog(
                                            LogEntity(
                                                title = "Generated Unit Test: $testFileName",
                                                source = "AI_TESTER",
                                                logContent = testCode,
                                                isError = false
                                            )
                                        )
                                        isTestGenerating = false
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberPurple),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp).testTag("editor_generate_test_button")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gen Test", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Search input if opened
                    if (isSearchOpen) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Cari kata kunci...", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f).height(44.dp),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                textStyle = TextStyle(fontSize = 12.sp)
                            )
                            IconButton(onClick = { isSearchOpen = false; searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Search", tint = TextMuted)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkCanvas)
        ) {
            // Floating AI Suggestion Bar (Above Keyboard)
            AnimatedVisibility(
                visible = aiSuggestion != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = DarkCardBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .testTag("editor_suggestion_bar"),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Saran AI:", fontSize = 11.sp, color = CyberCyan, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = aiSuggestion ?: "",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TextPrimary,
                                maxLines = 2
                            )
                        }

                        Row {
                            IconButton(onClick = { aiSuggestion = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = CyberRed)
                            }
                            Button(
                                onClick = {
                                    aiSuggestion?.let { suggestion ->
                                        undoStack.add(codeContent)
                                        codeContent = if (codeContent.endsWith("\n")) {
                                            codeContent + suggestion
                                        } else {
                                            codeContent + "\n" + suggestion
                                        }
                                        isDirty = true
                                        aiSuggestion = null
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberGreen, contentColor = Color.Black),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Sisipkan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Code Editor View with Line Numbers
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Line Numbers Gutter
                Column(
                    modifier = Modifier
                        .background(EditorGutterBg)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    for (i in 1..maxOf(lines.size, 1)) {
                        Text(
                            text = "$i",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted,
                            lineHeight = 20.sp
                        )
                    }
                }

                // Code Input Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 12.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    BasicTextField(
                        value = codeContent,
                        onValueChange = { newValue ->
                            if (codeContent != newValue) {
                                undoStack.add(codeContent)
                                redoStack.clear()
                                codeContent = newValue
                                isDirty = true
                            }
                        },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = TextPrimary,
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(CyberCyan),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("code_editor_text_field")
                    )
                }
            }
        }
    }

    // Generate Test Result Dialog
    if (showTestDialog) {
        AlertDialog(
            onDismissRequest = { showTestDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CyberPurple)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Test Generator")
                }
            },
            text = {
                if (isTestGenerating) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        CircularProgressIndicator(color = CyberPurple)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Gemini sedang membuat Unit & Robolectric Tests...")
                    }
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = "Test file berhasil dibuat dan disimpan ke /app/src/test/:\n",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = EditorGutterBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = generatedTestResult ?: "",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextPrimary,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showTestDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black)
                ) {
                    Text("Tutup", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
