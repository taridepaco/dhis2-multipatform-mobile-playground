package org.dhis2.multiplatformmobileplayground.dsl.llm

import org.dhis2.multiplatformmobileplayground.dsl.parser.DslParser

class DslInputResolver : InputResolver {
    override val isLlmPlatform: Boolean = false

    override suspend fun warmUp(onProgress: (InterpreterState) -> Unit): InterpreterState =
        InterpreterState.DslFallback

    override suspend fun resolve(text: String): InterpretResult = try {
        val invocation = DslParser.parse(text)
        InterpretResult.Resolved(invocation = invocation, inferredCall = null)
    } catch (e: IllegalArgumentException) {
        InterpretResult.Failure("Parse error: ${e.message}")
    }
}
