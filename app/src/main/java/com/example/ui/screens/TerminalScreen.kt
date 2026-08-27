package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.TerminalManager
import com.example.ui.theme.*
import kotlinx.coroutines.launch

data class TerminalLogLine(
    val command: String?,
    val output: String,
    val isCommand: Boolean = false,
    val isError: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    terminalManager: TerminalManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputCommand by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }

    val logs = remember {
        mutableStateListOf(
            TerminalLogLine(null, "=== rerev7 Mobile Linux Terminal v2.44 ==="),
            TerminalLogLine(null, "Environment: aarch64-linux-android | Working Directory: ~/AIStudioWorkspace"),
            TerminalLogLine(null, "Type 'help', 'git status', 'ls -la', or './gradlew assembleDebug'\n")
        )
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    fun runCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return

        history.add(trimmed)
        historyIndex = -1
        inputCommand = ""

        logs.add(TerminalLogLine(trimmed, "$ $trimmed", isCommand = true))

        scope.launch {
            val result = terminalManager.executeCommand(trimmed)
            if (result == "__CLEAR__") {
                logs.clear()
            } else {
                logs.add(TerminalLogLine(null, result))
            }
        }
    }

    // Special keyboard shortcut buttons
    val codingShortcuts = listOf(
        "|", "/", "-", "~", "Tab", "Ctrl", "Esc",
        "ls -la", "pwd", "git status", "./gradlew assembleDebug", "clear"
    )

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = CyberGreen, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Terminal CLI (Termux Engine)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(terminalManager.currentDirectory.name, fontSize = 11.sp, color = CyberGreen)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(TerminalBg)
        ) {
            // Terminal Console Output Window
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { log ->
                    Text(
                        text = log.output,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = when {
                            log.isCommand -> CyberCyan
                            log.isError -> CyberRed
                            else -> TerminalGreen
                        },
                        lineHeight = 16.sp
                    )
                }
            }

            // Keyboard Shortcut Bar (Coding Bar)
            Surface(
                color = DarkSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    codingShortcuts.forEach { key ->
                        Surface(
                            onClick = {
                                when (key) {
                                    "clear" -> runCommand("clear")
                                    "git status" -> runCommand("git status")
                                    "ls -la" -> runCommand("ls")
                                    "pwd" -> runCommand("pwd")
                                    "./gradlew assembleDebug" -> runCommand("./gradlew assembleDebug")
                                    "Tab" -> inputCommand += "  "
                                    else -> inputCommand += key
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = DarkCardBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                        ) {
                            Text(
                                text = key,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Terminal Input Field
            Surface(
                color = DarkSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$ ",
                        fontFamily = FontFamily.Monospace,
                        color = CyberGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    OutlinedTextField(
                        value = inputCommand,
                        onValueChange = { inputCommand = it },
                        placeholder = { Text("Ketik perintah shell...", fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("terminal_input_field"),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = TextPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberGreen,
                            unfocusedBorderColor = DarkBorder
                        )
                    )

                    IconButton(
                        onClick = { runCommand(inputCommand) },
                        modifier = Modifier.testTag("terminal_execute_button")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Run", tint = CyberGreen)
                    }
                }
            }
        }
    }
}
