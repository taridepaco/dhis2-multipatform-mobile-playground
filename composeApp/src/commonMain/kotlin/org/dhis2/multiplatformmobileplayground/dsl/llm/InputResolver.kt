package org.dhis2.multiplatformmobileplayground.dsl.llm

interface InputResolver {
    val isLlmPlatform: Boolean
    suspend fun warmUp(): InterpreterState
    suspend fun resolve(text: String): InterpretResult
}
