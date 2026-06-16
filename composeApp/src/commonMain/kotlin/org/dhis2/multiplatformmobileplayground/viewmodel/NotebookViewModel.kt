package org.dhis2.multiplatformmobileplayground.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.dhis2.multiplatformmobileplayground.dsl.executor.DslExecutor
import org.dhis2.multiplatformmobileplayground.dsl.llm.InputResolver
import org.dhis2.multiplatformmobileplayground.dsl.llm.InterpretResult
import org.dhis2.multiplatformmobileplayground.dsl.llm.InterpreterState
import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult
import org.dhis2.multiplatformmobileplayground.dsl.model.ExecutionEntry

class NotebookViewModel(
    private val resolver: InputResolver,
    private val executor: DslExecutor
) : ViewModel() {

    private val _history = MutableStateFlow<List<ExecutionEntry>>(emptyList())
    val history: StateFlow<List<ExecutionEntry>> = _history.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _interpreterState = MutableStateFlow<InterpreterState>(InterpreterState.Loading)
    val interpreterState: StateFlow<InterpreterState> = _interpreterState.asStateFlow()

    val isLlmPlatform: Boolean = resolver.isLlmPlatform

    private val pendingInputs = ArrayDeque<String>()
    private val executionMutex = Mutex()

    init {
        viewModelScope.launch {
            val state = resolver.warmUp { progress -> _interpreterState.value = progress }
            while (pendingInputs.isNotEmpty()) {
                executeInput(pendingInputs.removeFirst())
            }
            _interpreterState.value = state
        }
    }

    fun submit(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (isWarmingUp(_interpreterState.value)) {
            pendingInputs.addLast(trimmed)
            return
        }
        viewModelScope.launch { executeInput(trimmed) }
    }

    private suspend fun executeInput(text: String) = executionMutex.withLock {
        _isExecuting.value = true
        try {
            val entry = when (val resolution = resolver.resolve(text)) {
                is InterpretResult.Resolved -> {
                    val result = try {
                        executor.execute(resolution.invocation)
                    } catch (e: Exception) {
                        DslResult.Error("Unexpected error: ${e.message}")
                    }
                    ExecutionEntry(input = text, result = result, inferredCall = resolution.inferredCall)
                }
                is InterpretResult.Clarification -> ExecutionEntry(
                    input = text,
                    result = DslResult.Error(resolution.message),
                    inferredCall = null
                )
                is InterpretResult.Failure -> ExecutionEntry(
                    input = text,
                    result = DslResult.Error(resolution.message),
                    inferredCall = null
                )
            }
            _history.update { it + entry }
        } catch (t: kotlinx.coroutines.CancellationException) {
            throw t
        } catch (t: Throwable) {
            _history.update {
                it + ExecutionEntry(
                    input = text,
                    result = DslResult.Error("Unexpected error: ${t.message}"),
                    inferredCall = null
                )
            }
        } finally {
            _isExecuting.value = false
        }
    }

    private fun isWarmingUp(state: InterpreterState): Boolean =
        state == InterpreterState.Loading || state is InterpreterState.DownloadingModel
}
