package org.dhis2.multiplatformmobileplayground.dsl.llm

class LlmInputResolver(
    private val interpreter: NaturalLanguageInterpreter,
    private val fallback: DslInputResolver = DslInputResolver(),
    private val withDslFallback: Boolean = false
) : InputResolver {

    override val isLlmPlatform: Boolean = true

    private var useFallback: Boolean = withDslFallback

    override suspend fun warmUp(onProgress: (InterpreterState) -> Unit): InterpreterState {
        if (withDslFallback) return InterpreterState.DslFallback
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
