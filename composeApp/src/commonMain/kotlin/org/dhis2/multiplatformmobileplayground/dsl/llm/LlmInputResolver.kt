package org.dhis2.multiplatformmobileplayground.dsl.llm

class LlmInputResolver(
    private val interpreter: NaturalLanguageInterpreter,
    private val fallback: DslInputResolver = DslInputResolver()
) : InputResolver {

    override val isLlmPlatform: Boolean = true

    private var useFallback: Boolean = false

    override suspend fun warmUp(onProgress: (InterpreterState) -> Unit): InterpreterState {
        interpreter.warmUp(onProgress)
        return if (interpreter.isAvailable) {
            InterpreterState.Ready
        } else {
            useFallback = true
            InterpreterState.DslFallback
        }
    }

    override suspend fun resolve(text: String): InterpretResult {
        if (useFallback) return fallback.resolve(text)
        return interpreter.interpret(text)
    }
}
