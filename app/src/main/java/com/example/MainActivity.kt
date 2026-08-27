package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PreferencesManager
import com.example.service.*
import com.example.ui.screens.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class NavigationTab(val label: String, val icon: ImageVector, val tag: String) {
    CHAT("Chat AI", Icons.Default.Chat, "tab_chat"),
    EDITOR("Editor", Icons.Default.Code, "tab_editor"),
    GITHUB("GitHub", Icons.Default.CloudQueue, "tab_github"),
    TERMINAL("Terminal", Icons.Default.Terminal, "tab_terminal"),
    BUILD("Build", Icons.Default.RocketLaunch, "tab_build"),
    DB_VIEWER("DB Viewer", Icons.Default.Storage, "tab_db"),
    API_TESTER("API Tester", Icons.Default.Http, "tab_api"),
    LOGS("Logs & Review", Icons.Default.BugReport, "tab_logs")
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Jadwalkan Auto-Fix & Auto-Backup background worker
        AutoFixWorker.schedule(this)
        AutoBackupWorker.schedule(this)

        setContent {
            Rerev7Theme {
                MainAppHost(activity = this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppHost(activity: ComponentActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesManager(context) }

    val aiService = remember { AiService(context) }
    val githubService = remember { GithubService(context) }
    val projectManager = remember { ProjectManager(context) }
    val terminalManager = remember { TerminalManager(context) }

    var isOnboardingDone by remember { mutableStateOf(prefs.isOnboardingCompleted) }
    var currentTab by remember { mutableStateOf(NavigationTab.CHAT) }
    var editorInitialFile by remember { mutableStateOf<String?>(null) }

    // Dialog state
    var showLibraryManager by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf("blank") }

    // Inisialisasi workspace jika pertama kali dibuka
    LaunchedEffect(Unit) {
        projectManager.ensureDefaultWorkspace()
    }

    if (!isOnboardingDone) {
        OnboardingScreen(
            onFinish = {
                prefs.isOnboardingCompleted = true
                isOnboardingDone = true
            }
        )
        return
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DarkSurface,
                modifier = Modifier.width(300.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyberCyan.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Terminal, contentDescription = null, tint = CyberCyan)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("rerev7 Mobile IDE", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CyberCyan)
                            Text("Vibe Coding & Cloud Build", fontSize = 11.sp, color = TextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = DarkBorder)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("ALAT & FITUR TAMBAHAN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Spacer(modifier = Modifier.height(8.dp))

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Storage, contentDescription = null, tint = CyberCyan) },
                        label = { Text("DB Viewer (SQLite / Room)") },
                        selected = currentTab == NavigationTab.DB_VIEWER,
                        onClick = {
                            currentTab = NavigationTab.DB_VIEWER
                            scope.launch { drawerState.close() }
                        }
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Http, contentDescription = null, tint = CyberGreen) },
                        label = { Text("API Tester (Postman)") },
                        selected = currentTab == NavigationTab.API_TESTER,
                        onClick = {
                            currentTab = NavigationTab.API_TESTER
                            scope.launch { drawerState.close() }
                        }
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.BugReport, contentDescription = null, tint = CyberRed) },
                        label = { Text("Logs & Bug Hunter") },
                        selected = currentTab == NavigationTab.LOGS,
                        onClick = {
                            currentTab = NavigationTab.LOGS
                            scope.launch { drawerState.close() }
                        }
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Extension, contentDescription = null, tint = CyberPurple) },
                        label = { Text("Library Manager") },
                        selected = false,
                        onClick = {
                            showLibraryManager = true
                            scope.launch { drawerState.close() }
                        }
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = CyberAmber) },
                        label = { Text("Buat Proyek Baru") },
                        selected = false,
                        onClick = {
                            showNewProjectDialog = true
                            scope.launch { drawerState.close() }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = DarkBorder)
                    Spacer(modifier = Modifier.height(10.dp))

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Dns, contentDescription = null, tint = TextPrimary) },
                        label = { Text("Buka Logcat Real-time") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            context.startActivity(Intent(context, LogcatActivity::class.java))
                        }
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = TextPrimary) },
                        label = { Text("Pengaturan & API Keys") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            context.startActivity(Intent(context, SettingActivity::class.java))
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("main_drawer_button")
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                            Text(
                                text = "rerev7",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = CyberCyan
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showLibraryManager = true },
                                modifier = Modifier.testTag("top_library_button")
                            ) {
                                Icon(Icons.Default.Extension, contentDescription = "Libraries", tint = CyberPurple)
                            }
                            IconButton(
                                onClick = { context.startActivity(Intent(context, SettingActivity::class.java)) },
                                modifier = Modifier.testTag("top_settings_button")
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = DarkSurface,
                    contentColor = TextPrimary,
                    tonalElevation = 8.dp
                ) {
                    val mainTabs = listOf(
                        NavigationTab.CHAT,
                        NavigationTab.EDITOR,
                        NavigationTab.GITHUB,
                        NavigationTab.TERMINAL,
                        NavigationTab.BUILD
                    )

                    mainTabs.forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (isSelected) CyberCyan else TextMuted
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) CyberCyan else TextMuted
                                )
                            },
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (currentTab) {
                    NavigationTab.CHAT -> ChatScreen(
                        aiService = aiService,
                        projectManager = projectManager,
                        onNavigateToEditor = { targetPath ->
                            editorInitialFile = targetPath
                            currentTab = NavigationTab.EDITOR
                        }
                    )
                    NavigationTab.EDITOR -> EditorScreen(
                        aiService = aiService,
                        projectManager = projectManager,
                        initialFilePath = editorInitialFile
                    )
                    NavigationTab.GITHUB -> GithubScreen(
                        githubService = githubService,
                        aiService = aiService,
                        onNavigateToSettings = {
                            context.startActivity(Intent(context, SettingActivity::class.java))
                        }
                    )
                    NavigationTab.TERMINAL -> TerminalScreen(
                        terminalManager = terminalManager
                    )
                    NavigationTab.BUILD -> BuildScreen(
                        githubService = githubService
                    )
                    NavigationTab.DB_VIEWER -> DbViewerScreen()
                    NavigationTab.API_TESTER -> ApiTesterScreen()
                    NavigationTab.LOGS -> LogScreen(
                        aiService = aiService,
                        projectManager = projectManager,
                        onNavigateToChat = { currentTab = NavigationTab.CHAT }
                    )
                }
            }
        }
    }

    // Library Manager Modal Dialog
    if (showLibraryManager) {
        LibraryManagerDialog(
            projectManager = projectManager,
            onDismiss = { showLibraryManager = false }
        )
    }

    // New Project Dialog
    if (showNewProjectDialog) {
        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            title = { Text("Buat Proyek Baru") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text("Nama Proyek") },
                        modifier = Modifier.fillMaxWidth().testTag("new_project_name_input"),
                        singleLine = true
                    )

                    Text("Pilih Template:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    listOf("blank" to "Blank Activity Compose", "camera-app" to "CameraX Photo App", "api-app" to "REST API Client").forEach { (tid, tname) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedTemplate == tid,
                                onClick = { selectedTemplate = tid }
                            )
                            Text(tname, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProjectName.isNotBlank()) {
                            scope.launch {
                                projectManager.createProjectFromTemplate(selectedTemplate, newProjectName)
                                Toast.makeText(context, "Proyek '$newProjectName' dibuat!", Toast.LENGTH_SHORT).show()
                                showNewProjectDialog = false
                                newProjectName = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black)
                ) {
                    Text("Buat", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
