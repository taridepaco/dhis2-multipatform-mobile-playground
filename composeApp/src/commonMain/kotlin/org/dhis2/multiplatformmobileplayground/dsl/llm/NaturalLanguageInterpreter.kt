package org.dhis2.multiplatformmobileplayground.dsl.llm

interface NaturalLanguageInterpreter {
    val isAvailable: Boolean
    suspend fun warmUp()
    suspend fun interpret(text: String): InterpretResult
}
