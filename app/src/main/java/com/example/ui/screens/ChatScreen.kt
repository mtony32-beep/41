package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.AppDatabase
import com.example.data.entities.ChatMessage
import com.example.service.AiService
import com.example.service.ProjectFileItem
import com.example.service.ProjectManager
import com.example.service.SpeechService
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    aiService: AiService,
    projectManager: ProjectManager,
    onNavigateToEditor: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val messages by db.chatDao().getAllMessages().collectAsState(initial = emptyList())
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Project files dropdown
    var projectFiles by remember { mutableStateOf<List<ProjectFileItem>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<ProjectFileItem?>(null) }
    var isFileDropdownExpanded by remember { mutableStateOf(false) }

    // Vibe Coding Voice Mode
    var isListeningVoice by remember { mutableStateOf(false) }
    var speechService by remember { mutableStateOf<SpeechService?>(null) }

    // Load project files on mount
    LaunchedEffect(Unit) {
        projectFiles = projectManager.getAllProjectFiles()
        if (projectFiles.isNotEmpty()) {
            selectedFile = projectFiles.first()
        }
    }

    // Scroll to bottom on new message
    LaunchedEffect(messages.size, isStreaming) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        uri?.let {
            try {
                selectedBitmap = if (Build.VERSION.SDK_INT < 28) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal memuat gambar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Cleanup speech service
    DisposableEffect(Unit) {
        onDispose {
            speechService?.destroy()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Gemini Flash",
                                        tint = CyberCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Gemini 1.5 / 3.5 Flash",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Streaming • Multimodal • Vibe Coding",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CyberGreen
                                )
                            }
                        }

                        // Clear chat button
                        IconButton(
                            onClick = {
                                scope.launch {
                                    db.chatDao().clearAll()
                                    Toast.makeText(context, "Riwayat chat dibersihkan", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("chat_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Dropdown pilih file aktif & tombol "Perbaiki File Ini"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Dropdown File Picker
                        ExposedDropdownMenuBox(
                            expanded = isFileDropdownExpanded,
                            onExpandedChange = { isFileDropdownExpanded = !isFileDropdownExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = selectedFile?.name ?: "Pilih File...",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isFileDropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("chat_file_dropdown"),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                shape = RoundedCornerShape(8.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = isFileDropdownExpanded,
                                onDismissRequest = { isFileDropdownExpanded = false }
                            ) {
                                projectFiles.forEach { file ->
                                    DropdownMenuItem(
                                        text = { Text(file.relativePath, fontSize = 13.sp) },
                                        onClick = {
                                            selectedFile = file
                                            isFileDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Tombol "Perbaiki File Ini"
                        Button(
                            onClick = {
                                val file = selectedFile
                                if (file == null) {
                                    Toast.makeText(context, "Pilih file terlebih dahulu", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                scope.launch {
                                    val content = projectManager.readFile(file.relativePath)
                                    val prompt = "Tolong perbaiki, analisa error, dan optimasi file ${file.name} berikut:\n```kotlin\n$content\n```"
                                    
                                    db.chatDao().insertMessage(
                                        ChatMessage(
                                            sender = "USER",
                                            content = "Perbaiki file: ${file.relativePath}",
                                            attachedFile = file.relativePath
                                        )
                                    )

                                    isStreaming = true
                                    val streamMsgId = db.chatDao().insertMessage(
                                        ChatMessage(
                                            sender = "AI",
                                            content = "Sedang menganalisa & memperbaiki file ${file.name}...",
                                            attachedFile = file.relativePath
                                        )
                                    )

                                    val fullResult = StringBuilder()
                                    aiService.generateContentStream(prompt) { chunk ->
                                        fullResult.append(chunk)
                                        scope.launch {
                                            db.chatDao().insertMessage(
                                                ChatMessage(
                                                    id = streamMsgId,
                                                    sender = "AI",
                                                    content = fullResult.toString(),
                                                    attachedFile = file.relativePath,
                                                    generatedCode = fullResult.toString()
                                                )
                                            )
                                        }
                                    }
                                    isStreaming = false
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("chat_fix_file_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberPurple,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = "Fix", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Perbaiki", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        ) {
            // Chat Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "rerev7 AI Coding Assistant",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Mulai dengan mengetik prompt, lampirkan gambar desain UI untuk dibuatkan XML, atau tekan ikon Mikrofon untuk Vibe Coding.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                items(messages, key = { it.id }) { message ->
                    ChatMessageItem(
                        message = message,
                        onApplyCode = { code, filePath ->
                            scope.launch {
                                val path = filePath ?: selectedFile?.relativePath ?: "app/src/main/java/com/example/MainActivity.kt"
                                projectManager.saveFile(path, code)
                                Toast.makeText(context, "Kode berhasil disimpan ke $path!", Toast.LENGTH_LONG).show()
                                onNavigateToEditor(path)
                            }
                        }
                    )
                }
            }

            // Image Preview Bar (if attached)
            if (selectedImageUri != null) {
                Surface(
                    color = DarkSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Attached Image",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Gambar UI Designer terlampir",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberCyan
                            )
                        }
                        IconButton(onClick = {
                            selectedImageUri = null
                            selectedBitmap = null
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove Image", tint = CyberRed)
                        }
                    }
                }
            }

            // Bottom Input Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Attach Image button (AI Designer)
                    IconButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.testTag("chat_attach_image_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Attach Image",
                            tint = if (selectedImageUri != null) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Microphone Vibe Coding Button
                    IconButton(
                        onClick = {
                            if (isListeningVoice) {
                                speechService?.stopListening()
                                isListeningVoice = false
                            } else {
                                speechService = SpeechService(
                                    context = context,
                                    onResult = { spokenText ->
                                        inputText = spokenText
                                        isListeningVoice = false
                                        Toast.makeText(context, "Suara: $spokenText", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        isListeningVoice = false
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                )
                                speechService?.startListening()
                                isListeningVoice = true
                            }
                        },
                        modifier = Modifier.testTag("chat_voice_mic_button")
                    ) {
                        Icon(
                            imageVector = if (isListeningVoice) Icons.Default.Mic else Icons.Default.MicNone,
                            contentDescription = "Vibe Coding Mic",
                            tint = if (isListeningVoice) CyberRed else CyberCyan
                        )
                    }

                    // Input Text Field
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                if (isListeningVoice) "Mendengarkan suara..." else "Tanya AI atau minta koding...",
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_text_input"),
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = DarkBorder
                        )
                    )

                    // Send Button
                    IconButton(
                        onClick = {
                            val textToSend = inputText.trim()
                            if (textToSend.isBlank() && selectedBitmap == null) return@IconButton

                            scope.launch {
                                inputText = ""
                                val bitmap = selectedBitmap
                                val imgUri = selectedImageUri?.toString()
                                selectedBitmap = null
                                selectedImageUri = null

                                // Simpan pesan pengguna
                                db.chatDao().insertMessage(
                                    ChatMessage(
                                        sender = "USER",
                                        content = textToSend.ifBlank { "Buatkan XML dari gambar UI ini" },
                                        attachedFile = selectedFile?.relativePath,
                                        attachedImageUri = imgUri
                                    )
                                )

                                isStreaming = true

                                if (bitmap != null) {
                                    // Mode AI Designer Image to XML
                                    val aiMsgId = db.chatDao().insertMessage(
                                        ChatMessage(
                                            sender = "AI",
                                            content = "Sedang merancang Layout XML & Compose dari gambar...",
                                            attachedImageUri = imgUri
                                        )
                                    )
                                    val prompt = textToSend.ifBlank { "Buatkan layout XML Android dan Composable Jetpack Compose dari screenshot gambar ini secara lengkap dan rapi." }
                                    val generatedCode = aiService.generateUiFromImage(bitmap, prompt)
                                    db.chatDao().insertMessage(
                                        ChatMessage(
                                            id = aiMsgId,
                                            sender = "AI",
                                            content = generatedCode,
                                            generatedCode = generatedCode,
                                            attachedImageUri = imgUri
                                        )
                                    )
                                } else {
                                    // Mode Standar / Vibe Coding Streaming
                                    val aiMsgId = db.chatDao().insertMessage(
                                        ChatMessage(
                                            sender = "AI",
                                            content = "Mengetik jawaban...",
                                            attachedFile = selectedFile?.relativePath
                                        )
                                    )

                                    val fullStream = StringBuilder()
                                    val fileContext = selectedFile?.let {
                                        "\n[File Aktif: ${it.relativePath}]\n" + projectManager.readFile(it.relativePath)
                                    } ?: ""

                                    val fullPrompt = "$textToSend\n$fileContext"

                                    aiService.generateContentStream(fullPrompt) { chunk ->
                                        fullStream.append(chunk)
                                        scope.launch {
                                            db.chatDao().insertMessage(
                                                ChatMessage(
                                                    id = aiMsgId,
                                                    sender = "AI",
                                                    content = fullStream.toString(),
                                                    attachedFile = selectedFile?.relativePath,
                                                    generatedCode = if (fullStream.contains("```")) fullStream.toString() else null
                                                )
                                            )
                                        }
                                    }
                                }
                                isStreaming = false
                            }
                        },
                        enabled = !isStreaming,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isStreaming) MaterialTheme.colorScheme.surfaceVariant else CyberCyan)
                            .testTag("chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (isStreaming) MaterialTheme.colorScheme.onSurfaceVariant else Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onApplyCode: (code: String, targetFile: String?) -> Unit
) {
    val isUser = message.sender == "USER"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) CyberCyan.copy(alpha = 0.15f) else DarkCardBg,
            border = if (isUser) androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)) else null,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isUser) Icons.Default.Person else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (isUser) CyberCyan else CyberPurple,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isUser) "Kamu" else "Gemini Flash",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) CyberCyan else CyberPurple
                    )
                    if (message.attachedFile != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = message.attachedFile,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Attached image thumbnail
                if (message.attachedImageUri != null) {
                    AsyncImage(
                        model = message.attachedImageUri,
                        contentDescription = "Attached",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Message Text
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    lineHeight = 20.sp
                )

                // If code is detected, show "Terapkan Kode ke File" button
                if (!isUser && message.content.contains("```")) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val extractedCode = remember(message.content) {
                        val regex = Regex("```(?:[a-zA-Z]+)?\\n([\\s\\S]*?)```")
                        regex.find(message.content)?.groupValues?.get(1) ?: message.content
                    }

                    Button(
                        onClick = { onApplyCode(extractedCode, message.attachedFile) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberGreen,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("apply_code_button"),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Terapkan Kode ke File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
