package org.dhis2.multiplatformmobileplayground.dsl.llm

import kotlinx.coroutines.test.runTest
import org.dhis2.multiplatformmobileplayground.dsl.model.Invocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class LlmInputResolverTest {

    @Test
    fun shouldReturnResolvedWithInferredCallWhenLlmSucceeds() = runTest {
        val interpreter = FakeNaturalLanguageInterpreter(
            availableOnConstruction = true,
            responses = ArrayDeque(listOf(
                InterpretResult.Resolved(
                    invocation = Invocation("d2.programs.list", listOf("50")),
                    inferredCall = "d2.programs.list(50)"
                )
            ))
        )
        val resolver = LlmInputResolver(interpreter)
        resolver.warmUp()

        val result = resolver.resolve("show me the first 50 programs")
        val resolved = assertIs<InterpretResult.Resolved>(result)
        assertEquals("d2.programs.list(50)", resolved.inferredCall)
        assertEquals(listOf("50"), resolved.invocation.args)
    }

    @Test
    fun shouldReturnClarificationWhenLlmCannotMapRequest() = runTest {
        val interpreter = FakeNaturalLanguageInterpreter(
            availableOnConstruction = true,
            responses = ArrayDeque(listOf(
                InterpretResult.Clarification("I cannot map that request.")
            ))
        )
        val resolver = LlmInputResolver(interpreter)
        resolver.warmUp()

        val result = resolver.resolve("make me a sandwich")
        assertIs<InterpretResult.Clarification>(result)
    }

    @Test
    fun shouldFallBackToDslWhenWithDslFallbackTrue() = runTest {
        val interpreter = FakeNaturalLanguageInterpreter(availableOnConstruction = false)
        val resolver = LlmInputResolver(
            interpreter = interpreter,
            withDslFallback = true
        )
        val state = resolver.warmUp()

        assertEquals(InterpreterState.DslFallback, state)
        // DSL fallback: typed DSL command resolves via DslParser
        val result = resolver.resolve("help")
        val resolved = assertIs<InterpretResult.Resolved>(result)
        assertEquals("help", resolved.invocation.commandName)
        // DSL mode has no inferredCall
        assertNull(resolved.inferredCall)
    }

    @Test
    fun shouldReturnDslFallbackStateWhenGemmaUnavailableAfterWarmUp() = runTest {
        val interpreter = FakeNaturalLanguageInterpreter(availableOnConstruction = false)
        val resolver = LlmInputResolver(interpreter = interpreter)
        val state = resolver.warmUp()

        assertEquals(InterpreterState.DslFallback, state)
    }

    @Test
    fun shouldReturnReadyStateWhenGemmaAvailableAfterWarmUp() = runTest {
        val interpreter = FakeNaturalLanguageInterpreter(availableOnConstruction = true)
        val resolver = LlmInputResolver(interpreter = interpreter)
        val state = resolver.warmUp()

        assertEquals(InterpreterState.Ready, state)
    }

    @Test
    fun shouldExposeIsLlmPlatformTrue() {
        val resolver = LlmInputResolver(FakeNaturalLanguageInterpreter(false))
        assert(resolver.isLlmPlatform)
    }

    @Test
    fun shouldDelegateToFallbackAfterUnavailableWarmUp() = runTest {
        val interpreter = FakeNaturalLanguageInterpreter(availableOnConstruction = false)
        val resolver = LlmInputResolver(interpreter = interpreter)
        resolver.warmUp()

        // After warmUp sets useFallback=true, DSL resolver handles input
        val result = resolver.resolve("help")
        val resolved = assertIs<InterpretResult.Resolved>(result)
        assertEquals("help", resolved.invocation.commandName)
    }
}

private class FakeNaturalLanguageInterpreter(
    availableOnConstruction: Boolean,
    private val responses: ArrayDeque<InterpretResult> = ArrayDeque()
) : NaturalLanguageInterpreter {
    override val isAvailable: Boolean = availableOnConstruction
    override suspend fun warmUp(onProgress: (InterpreterState) -> Unit) = Unit
    override suspend fun interpret(text: String): InterpretResult =
        responses.removeFirstOrNull() ?: InterpretResult.Failure("No more fake responses")
}
