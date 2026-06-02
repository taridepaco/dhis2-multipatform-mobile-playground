package org.dhis2.multiplatformmobileplayground.dsl.llm

interface NaturalLanguageInterpreter {
    val isAvailable: Boolean

    /**
     * Prepares the interpreter, downloading and loading the model if needed. [onProgress] receives
     * intermediate states (e.g. [InterpreterState.DownloadingModel]) while warming up.
     */
    suspend fun warmUp(onProgress: (InterpreterState) -> Unit = {})

    suspend fun interpret(text: String): InterpretResult
}
