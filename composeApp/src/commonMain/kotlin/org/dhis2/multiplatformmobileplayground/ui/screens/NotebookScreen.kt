package org.dhis2.multiplatformmobileplayground.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dhis2.multiplatformmobileplayground.dsl.format.ResultFormatter
import org.dhis2.multiplatformmobileplayground.dsl.llm.InterpreterState
import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult
import org.dhis2.multiplatformmobileplayground.dsl.model.ExecutionEntry
import org.dhis2.multiplatformmobileplayground.viewmodel.NotebookViewModel

@Composable
fun NotebookScreen(
    viewModel: NotebookViewModel,
    modifier: Modifier = Modifier
) {
    val history by viewModel.history.collectAsState()
    val isExecuting by viewModel.isExecuting.collectAsState()
    val interpreterState by viewModel.interpreterState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showPrivacyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.animateScrollToItem(history.size - 1)
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("On-device processing") },
            text = {
                Text(
                    "Your Notebook input is processed entirely on this device by Gemma 4. " +
                    "Nothing is sent to a server."
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("OK") }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Interpreter mode banner
        if (interpreterState == InterpreterState.DslFallback && viewModel.isLlmPlatform) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Natural language unavailable on this device — type DSL commands directly. Try `help`.",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            items(history) { entry ->
                NotebookEntryItem(entry = entry)
            }
        }

        if (isExecuting) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Thinking…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val downloadState = interpreterState as? InterpreterState.DownloadingModel
            val isLoading = interpreterState == InterpreterState.Loading || downloadState != null
            val placeholder = when {
                downloadState != null -> {
                    val pct = downloadState.progress?.let { " ${(it * 100).toInt()}%" } ?: ""
                    "Downloading model…$pct"
                }
                interpreterState == InterpreterState.Loading -> "Loading language model…"
                viewModel.isLlmPlatform && interpreterState == InterpreterState.Ready ->
                    "Ask in natural language or type a command…"
                else -> "Type a command… (try 'help')"
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder) },
                singleLine = true,
                enabled = !isExecuting && !isLoading,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            )
            if (viewModel.isLlmPlatform && interpreterState == InterpreterState.Ready) {
                IconButton(onClick = { showPrivacyDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Privacy information"
                    )
                }
            }
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.submit(inputText)
                        inputText = ""
                    }
                },
                enabled = !isExecuting && !isLoading && inputText.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Execute"
                )
            }
        }
    }
}

@Composable
private fun NotebookEntryItem(entry: ExecutionEntry) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "> ${entry.input}",
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
            color = MaterialTheme.colorScheme.primary
        )
        if (entry.inferredCall != null) {
            Text(
                text = "-> ${entry.inferredCall}",
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        val resultColor = when (entry.result) {
            is DslResult.Success -> MaterialTheme.colorScheme.onSurface
            is DslResult.Error -> MaterialTheme.colorScheme.error
        }
        SelectionContainer {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = ResultFormatter.formatForDisplay(entry.result),
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    color = resultColor,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}
