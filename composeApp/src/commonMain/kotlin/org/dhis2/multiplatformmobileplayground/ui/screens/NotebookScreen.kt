package org.dhis2.multiplatformmobileplayground.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.animateScrollToItem(history.size - 1)
    }

    Column(modifier = modifier.fillMaxSize()) {
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a command… (try 'help')") },
                singleLine = true,
                enabled = !isExecuting,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            )
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.submit(inputText)
                        inputText = ""
                    }
                },
                enabled = !isExecuting && inputText.isNotBlank()
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
    }
}
