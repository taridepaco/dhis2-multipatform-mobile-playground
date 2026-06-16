package org.dhis2.multiplatformmobileplayground.dsl.llm

sealed interface InterpreterState {
    data object Loading : InterpreterState

    /** The on-device model is being downloaded. [progress] is 0f..1f, or null when indeterminate. */
    data class DownloadingModel(val progress: Float?) : InterpreterState

    data object Ready : InterpreterState
    data object DslFallback : InterpreterState
}
