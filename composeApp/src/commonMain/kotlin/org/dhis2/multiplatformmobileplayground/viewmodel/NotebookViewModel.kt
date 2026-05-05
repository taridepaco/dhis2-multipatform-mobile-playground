package org.dhis2.multiplatformmobileplayground.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.dhis2.multiplatformmobileplayground.dsl.executor.DslExecutor
import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult
import org.dhis2.multiplatformmobileplayground.dsl.model.ExecutionEntry
import org.dhis2.multiplatformmobileplayground.dsl.parser.DslParser

class NotebookViewModel(
    private val executor: DslExecutor
) : ViewModel() {

    private val _history = MutableStateFlow<List<ExecutionEntry>>(emptyList())
    val history: StateFlow<List<ExecutionEntry>> = _history.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    fun submit(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            _isExecuting.value = true
            val result = try {
                val invocation = DslParser.parse(trimmed)
                executor.execute(invocation)
            } catch (e: IllegalArgumentException) {
                DslResult.Error("Parse error: ${e.message}")
            } catch (e: Exception) {
                DslResult.Error("Unexpected error: ${e.message}")
            }
            _history.update { it + ExecutionEntry(input = trimmed, result = result) }
            _isExecuting.value = false
        }
    }
}
