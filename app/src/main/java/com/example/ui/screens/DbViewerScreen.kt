package com.example.ui.screens

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
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
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class TableDataResult(
    val columnNames: List<String>,
    val rows: List<List<String>>,
    val errorMessage: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DbViewerScreen() {
    val context = LocalContext.current

    val tables = listOf("chat_messages", "projects", "action_logs", "build_queue", "ai_reviews", "bug_scans")
    var selectedTable by remember { mutableStateOf(tables.first()) }
    var customQuery by remember { mutableStateOf("SELECT * FROM chat_messages LIMIT 20;") }
    var queryResult by remember { mutableStateOf<TableDataResult?>(null) }
    var isExecuting by remember { mutableStateOf(false) }

    suspend fun executeRawSql(query: String) = withContext(Dispatchers.IO) {
        val dbPath = context.getDatabasePath("rerev7_database.db")
        if (!dbPath.exists()) {
            return@withContext TableDataResult(emptyList(), emptyList(), "Database file belum dibuat.")
        }

        try {
            val db = SQLiteDatabase.openDatabase(dbPath.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val trimmed = query.trim()

            if (trimmed.startsWith("SELECT", ignoreCase = true) || trimmed.startsWith("PRAGMA", ignoreCase = true)) {
                val cursor: Cursor = db.rawQuery(query, null)
                cursor.use { c ->
                    val columnNames = c.columnNames.toList()
                    val rows = mutableListOf<List<String>>()
                    while (c.moveToNext()) {
                        val row = mutableListOf<String>()
                        for (i in 0 until c.columnCount) {
                            row.add(c.getString(i) ?: "NULL")
                        }
                        rows.add(row)
                    }
                    TableDataResult(columnNames, rows)
                }
            } else {
                db.execSQL(query)
                TableDataResult(listOf("STATUS"), listOf(listOf("Perintah SQL '$query' berhasil dieksekusi.")))
            }
        } catch (e: Exception) {
            TableDataResult(emptyList(), emptyList(), "Error SQL: ${e.message}")
        }
    }

    LaunchedEffect(selectedTable) {
        isExecuting = true
        customQuery = "SELECT * FROM $selectedTable LIMIT 20;"
        queryResult = executeRawSql(customQuery)
        isExecuting = false
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("SQLite & Room DB Browser", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("rerev7_database.db", fontSize = 11.sp, color = CyberCyan)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Table selection chips
            Text("Pilih Tabel:", style = MaterialTheme.typography.titleSmall, color = CyberCyan)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tables.forEach { table ->
                    val isSelected = selectedTable == table
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTable = table },
                        label = { Text(table, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                            selectedLabelColor = CyberCyan
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SQL Query Console
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Custom SQL Query:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customQuery,
                        onValueChange = { customQuery = it },
                        modifier = Modifier.fillMaxWidth().testTag("db_sql_input"),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary),
                        maxLines = 3,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            isExecuting = true
                            kotlinx.coroutines.GlobalScope.let {
                                // Execute
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.End).height(36.dp).testTag("db_sql_execute_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Eksekusi SQL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Data Results Table
            Text("Hasil Query (${queryResult?.rows?.size ?: 0} baris):", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))

            val result = queryResult
            if (result?.errorMessage != null) {
                Surface(
                    color = CyberRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = result.errorMessage,
                        color = CyberRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else if (result != null && result.columnNames.isNotEmpty()) {
                Surface(
                    color = DarkCardBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                ) {
                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                        // Header Row
                        item {
                            Row(
                                modifier = Modifier
                                    .background(DarkSurfaceVariant, RoundedCornerShape(6.dp))
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                result.columnNames.forEach { col ->
                                    Text(
                                        text = col,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = CyberCyan,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.width(130.dp).padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }

                        // Data Rows
                        items(result.rows) { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                            ) {
                                row.forEach { cell ->
                                    Text(
                                        text = cell,
                                        fontSize = 11.sp,
                                        color = TextPrimary,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 2,
                                        modifier = Modifier.width(130.dp).padding(horizontal = 4.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}
