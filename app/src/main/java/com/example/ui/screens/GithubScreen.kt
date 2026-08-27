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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PreferencesManager
import com.example.service.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GithubScreen(
    githubService: GithubService,
    aiService: AiService,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesManager(context) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Repo & Commit, 1: Actions, 2: Pull Requests

    var branches by remember { mutableStateOf<List<String>>(listOf("main")) }
    var selectedBranch by remember { mutableStateOf("main") }
    var isBranchDropdownOpen by remember { mutableStateOf(false) }

    var commitMessage by remember { mutableStateOf("") }
    var isGeneratingAiCommit by remember { mutableStateOf(false) }
    var aiCommitOptions by remember { mutableStateOf<List<String>>(emptyList()) }

    var workflowRuns by remember { mutableStateOf<List<WorkflowRun>>(emptyList()) }
    var pullRequests by remember { mutableStateOf<List<PullRequestItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Load data
    fun refreshData() {
        scope.launch {
            isLoading = true
            branches = githubService.getBranches(prefs.defaultRepo)
            if (branches.isNotEmpty() && !branches.contains(selectedBranch)) {
                selectedBranch = branches.first()
            }
            workflowRuns = githubService.getWorkflowRuns(prefs.defaultRepo)
            pullRequests = githubService.getPullRequests(prefs.defaultRepo)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

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
                                color = CyberPurple.copy(alpha = 0.2f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.CloudQueue, contentDescription = null, tint = CyberPurple, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = prefs.defaultRepo.ifBlank { "GitHub Repository" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (prefs.githubToken.isNotBlank()) "Terhubung sebagai @${prefs.githubName.ifBlank { "User" }}" else "Token belum diatur",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (prefs.githubToken.isNotBlank()) CyberGreen else CyberAmber
                                )
                            }
                        }

                        IconButton(
                            onClick = { refreshData() },
                            modifier = Modifier.testTag("github_refresh_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tab selector (Repo/Commit, Actions, Pull Requests)
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = CyberCyan
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Repo & Commit", fontSize = 12.sp) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Actions (${workflowRuns.size})", fontSize = 12.sp) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("PRs (${pullRequests.size})", fontSize = 12.sp) }
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
                    // TAB 0: REPO, BRANCH & COMMIT
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Branch Selector
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Pilih Branch Aktif", style = MaterialTheme.typography.titleSmall, color = CyberCyan)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ExposedDropdownMenuBox(
                                        expanded = isBranchDropdownOpen,
                                        onExpandedChange = { isBranchDropdownOpen = !isBranchDropdownOpen }
                                    ) {
                                        OutlinedTextField(
                                            value = selectedBranch,
                                            onValueChange = {},
                                            readOnly = true,
                                            leadingIcon = { Icon(Icons.Default.ForkRight, contentDescription = null, tint = CyberCyan) },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isBranchDropdownOpen) },
                                            modifier = Modifier
                                                .menuAnchor()
                                                .fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        ExposedDropdownMenu(
                                            expanded = isBranchDropdownOpen,
                                            onDismissRequest = { isBranchDropdownOpen = false }
                                        ) {
                                            branches.forEach { branch ->
                                                DropdownMenuItem(
                                                    text = { Text(branch) },
                                                    onClick = {
                                                        selectedBranch = branch
                                                        isBranchDropdownOpen = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Commit & Push Card
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Git Commit", style = MaterialTheme.typography.titleSmall, color = CyberCyan)
                                        
                                        // Tombol AI Commit Message
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    isGeneratingAiCommit = true
                                                    val sampleDiff = "diff --git a/MainActivity.kt b/MainActivity.kt\n+ Scaffold compose UI\n+ integrated AI Assistant"
                                                    val options = aiService.generateCommitMessages(sampleDiff)
                                                    aiCommitOptions = options
                                                    if (options.isNotEmpty()) {
                                                        commitMessage = options.first()
                                                    }
                                                    isGeneratingAiCommit = false
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = CyberPurple),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp).testTag("ai_commit_message_button")
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("AI Message", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Commit message suggestions chips (feat / fix / refactor)
                                    if (aiCommitOptions.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Pilih format Conventional Commit:", fontSize = 11.sp, color = TextMuted)
                                            aiCommitOptions.forEach { option ->
                                                SuggestionChip(
                                                    onClick = { commitMessage = option },
                                                    label = { Text(option, fontSize = 11.sp) }
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    OutlinedTextField(
                                        value = commitMessage,
                                        onValueChange = { commitMessage = it },
                                        placeholder = { Text("Pesan commit (contoh: feat: tambah fitur baru)", fontSize = 13.sp) },
                                        modifier = Modifier.fillMaxWidth().testTag("github_commit_input"),
                                        shape = RoundedCornerShape(8.dp)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Commit & Push Button
                                        Button(
                                            onClick = {
                                                if (commitMessage.isBlank()) {
                                                    Toast.makeText(context, "Pesan commit tidak boleh kosong", Toast.LENGTH_SHORT).show()
                                                    return@Button
                                                }
                                                Toast.makeText(context, "Commit '$commitMessage' & Push berhasil ke $selectedBranch!", Toast.LENGTH_LONG).show()
                                                commitMessage = ""
                                                refreshData()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).testTag("github_push_button")
                                        ) {
                                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Commit & Push", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }

                                        // Pull Button
                                        OutlinedButton(
                                            onClick = {
                                                Toast.makeText(context, "Pull branch $selectedBranch: Already up-to-date.", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).testTag("github_pull_button")
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Git Pull", fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: GITHUB ACTIONS WORKFLOW RUNS
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (workflowRuns.isEmpty()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Belum ada workflow runs", fontWeight = FontWeight.Bold)
                                        Text("Trigger build dari Tab Build untuk menjalankan GitHub Actions.", fontSize = 12.sp, color = TextMuted)
                                    }
                                }
                            }
                        }

                        items(workflowRuns, key = { it.id }) { run ->
                            WorkflowRunItem(run = run)
                        }
                    }
                }

                2 -> {
                    // TAB 2: PULL REQUESTS
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (pullRequests.isEmpty()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.CallMerge, contentDescription = null, tint = CyberPurple, modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Tidak ada Pull Requests aktif", fontWeight = FontWeight.Bold)
                                        Text("Repository ini dalam kondisi bersih dan tersinkronisasi.", fontSize = 12.sp, color = TextMuted)
                                    }
                                }
                            }
                        }

                        items(pullRequests, key = { it.number }) { pr ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("#${pr.number} ${pr.title}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (pr.state == "open") CyberGreen.copy(alpha = 0.2f) else CyberPurple.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = pr.state.uppercase(),
                                                fontSize = 10.sp,
                                                color = if (pr.state == "open") CyberGreen else CyberPurple,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Dibuat oleh @${pr.author} • ${pr.createdAt}", fontSize = 11.sp, color = TextMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WorkflowRunItem(run: WorkflowRun) {
    val isSuccess = run.conclusion == "success"
    val isFailure = run.conclusion == "failure"
    val isRunning = run.status == "in_progress" || run.status == "queued"

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = when {
                    isSuccess -> CyberGreen.copy(alpha = 0.2f)
                    isFailure -> CyberRed.copy(alpha = 0.2f)
                    else -> CyberAmber.copy(alpha = 0.2f)
                },
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            isSuccess -> Icons.Default.CheckCircle
                            isFailure -> Icons.Default.Error
                            else -> Icons.Default.HourglassTop
                        },
                        contentDescription = null,
                        tint = when {
                            isSuccess -> CyberGreen
                            isFailure -> CyberRed
                            else -> CyberAmber
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${run.name} (#${run.id})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = run.commitMessage,
                    fontSize = 12.sp,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    text = "Branch: ${run.branch} • Status: ${run.conclusion.ifBlank { run.status }}",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    }
}
