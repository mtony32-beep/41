package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.ApiResponse
import com.example.service.ApiTesterService
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiTesterScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val apiService = remember { ApiTesterService() }

    val methods = listOf("GET", "POST", "PUT", "DELETE", "PATCH")
    var selectedMethod by remember { mutableStateOf("GET") }
    var isMethodDropdownOpen by remember { mutableStateOf(false) }

    var urlInput by remember { mutableStateOf("https://api.github.com/zen") }
    var headersInput by remember { mutableStateOf("User-Agent: rerev7-Mobile-Postman\nAccept: application/vnd.github+json") }
    var bodyInput by remember { mutableStateOf("{\n  \"title\": \"Hello rerev7\",\n  \"status\": \"active\"\n}") }

    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Headers, 1: Body, 2: Response

    var apiResponse by remember { mutableStateOf<ApiResponse?>(null) }
    var isLoading by remember { mutableStateOf(false) }

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
                    Icon(Icons.Default.Http, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("API Tester (Postman Mobile)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("HTTP REST Client & JSON Inspector", fontSize = 11.sp, color = CyberGreen)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(14.dp)
        ) {
            // URL & Method Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Method Selector Dropdown
                ExposedDropdownMenuBox(
                    expanded = isMethodDropdownOpen,
                    onExpandedChange = { isMethodDropdownOpen = !isMethodDropdownOpen },
                    modifier = Modifier.width(105.dp)
                ) {
                    OutlinedTextField(
                        value = selectedMethod,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isMethodDropdownOpen) },
                        modifier = Modifier.menuAnchor(),
                        textStyle = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = when (selectedMethod) {
                                "GET" -> CyberGreen
                                "POST" -> CyberCyan
                                "PUT" -> CyberAmber
                                "DELETE" -> CyberRed
                                else -> CyberPurple
                            }
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = isMethodDropdownOpen,
                        onDismissRequest = { isMethodDropdownOpen = false }
                    ) {
                        methods.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    selectedMethod = m
                                    isMethodDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                // URL Input
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("https://api.example.com", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f).testTag("api_url_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                )

                // Send Button
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            selectedSubTab = 2 // Switch to response tab

                            val headersMap = mutableMapOf<String, String>()
                            headersInput.lines().forEach { line ->
                                if (line.contains(":")) {
                                    val parts = line.split(":", limit = 2)
                                    headersMap[parts[0].trim()] = parts[1].trim()
                                }
                            }

                            val res = apiService.executeRequest(
                                url = urlInput,
                                method = selectedMethod,
                                headersMap = headersMap,
                                bodyContent = if (selectedMethod != "GET") bodyInput else null
                            )
                            apiResponse = res
                            isLoading = false
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(54.dp).testTag("api_send_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-tabs (Headers, Body, Response)
            TabRow(
                selectedTabIndex = selectedSubTab,
                containerColor = DarkSurface,
                contentColor = CyberCyan
            ) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = { Text("Headers", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = { Text("Body (JSON)", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedSubTab == 2,
                    onClick = { selectedSubTab = 2 },
                    text = {
                        Text(
                            text = "Response " + (apiResponse?.let { "[${it.statusCode}]" } ?: ""),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (apiResponse?.isSuccess == true) CyberGreen else if (apiResponse != null) CyberRed else CyberCyan
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tab Content
            when (selectedSubTab) {
                0 -> {
                    // Headers Input
                    Column {
                        Text("Custom Request Headers (Key: Value per baris):", fontSize = 11.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = headersInput,
                            onValueChange = { headersInput = it },
                            modifier = Modifier.fillMaxSize().testTag("api_headers_input"),
                            shape = RoundedCornerShape(8.dp),
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary)
                        )
                    }
                }
                1 -> {
                    // Body Input
                    Column {
                        Text("Request Body (JSON Payload):", fontSize = 11.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = bodyInput,
                            onValueChange = { bodyInput = it },
                            modifier = Modifier.fillMaxSize().testTag("api_body_input"),
                            shape = RoundedCornerShape(8.dp),
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary)
                        )
                    }
                }
                2 -> {
                    // Response Inspector
                    val response = apiResponse
                    if (response == null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Belum ada request yang dikirim.", color = TextMuted, fontSize = 13.sp)
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Status bar (Status code, Latency)
                            Surface(
                                color = DarkCardBg,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (response.isSuccess) CyberGreen.copy(alpha = 0.2f) else CyberRed.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "${response.statusCode} ${response.statusMessage}",
                                                color = if (response.isSuccess) CyberGreen else CyberRed,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "${response.durationMs} ms",
                                            fontSize = 12.sp,
                                            color = TextMuted
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(response.body))
                                            Toast.makeText(context, "Response disalin ke clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("Salin", fontSize = 11.sp, color = CyberCyan)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Response Body Area
                            Surface(
                                color = EditorGutterBg,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = response.body,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
