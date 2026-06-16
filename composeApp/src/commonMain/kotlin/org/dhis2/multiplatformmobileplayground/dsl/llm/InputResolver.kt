package org.dhis2.multiplatformmobileplayground.dsl.llm

interface InputResolver {
    val isLlmPlatform: Boolean

    /**
     * Prepares the resolver for use. [onProgress] receives intermediate states (e.g. model
     * download progress) before the terminal state is returned.
     */
    suspend fun warmUp(onProgress: (InterpreterState) -> Unit = {}): InterpreterState

    suspend fun resolve(text: String): InterpretResult
}
