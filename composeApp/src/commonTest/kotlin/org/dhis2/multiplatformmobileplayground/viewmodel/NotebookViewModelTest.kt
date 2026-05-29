package org.dhis2.multiplatformmobileplayground.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.dhis2.multiplatformmobileplayground.dsl.catalog.CommandRegistry
import org.dhis2.multiplatformmobileplayground.dsl.executor.DslExecutor
import org.dhis2.multiplatformmobileplayground.dsl.llm.InputResolver
import org.dhis2.multiplatformmobileplayground.dsl.llm.InterpretResult
import org.dhis2.multiplatformmobileplayground.dsl.llm.InterpreterState
import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult
import org.dhis2.multiplatformmobileplayground.dsl.model.Invocation
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotebookViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: NotebookViewModel
    private lateinit var fakeExecutor: FakeDslExecutor
    private lateinit var fakeResolver: FakeInputResolver

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeExecutor = FakeDslExecutor()
        fakeResolver = FakeInputResolver()
        viewModel = NotebookViewModel(fakeResolver, fakeExecutor)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun shouldStartWithEmptyHistoryAndLoadingState() {
        assertTrue(viewModel.history.value.isEmpty())
        assertFalse(viewModel.isExecuting.value)
        assertEquals(InterpreterState.Loading, viewModel.interpreterState.value)
    }

    @Test
    fun shouldTransitionFromLoadingToReadyAfterWarmUp() = runTest {
        fakeResolver.warmUpState = InterpreterState.Ready
        viewModel = NotebookViewModel(fakeResolver, fakeExecutor)
        advanceUntilIdle()

        assertEquals(InterpreterState.Ready, viewModel.interpreterState.value)
    }

    @Test
    fun shouldTransitionToDslFallbackWhenLlmUnavailable() = runTest {
        fakeResolver.warmUpState = InterpreterState.DslFallback
        viewModel = NotebookViewModel(fakeResolver, fakeExecutor)
        advanceUntilIdle()

        assertEquals(InterpreterState.DslFallback, viewModel.interpreterState.value)
    }

    @Test
    fun shouldQueueSubmissionsReceivedDuringLoading() = runTest {
        fakeResolver.warmUpState = InterpreterState.Ready
        fakeResolver.resolveResult = InterpretResult.Resolved(
            invocation = Invocation("help", emptyList()),
            inferredCall = null
        )
        fakeExecutor.result = DslResult.Success(json = "{}", display = "ok")

        viewModel = NotebookViewModel(fakeResolver, fakeExecutor)
        // submit while still in Loading state (warmUp not yet run)
        viewModel.submit("help")
        advanceUntilIdle()

        assertEquals(1, viewModel.history.value.size)
        assertEquals("help", viewModel.history.value[0].input)
    }

    @Test
    fun shouldAppendEntryToHistoryAfterSuccessfulExecution() = runTest {
        fakeResolver.warmUpState = InterpreterState.Ready
        fakeResolver.resolveResult = InterpretResult.Resolved(
            invocation = Invocation("help", emptyList()),
            inferredCall = null
        )
        fakeExecutor.result = DslResult.Success(json = "{}", display = "ok")
        viewModel = NotebookViewModel(fakeResolver, fakeExecutor)
        advanceUntilIdle()

        viewModel.submit("help")
        advanceUntilIdle()

        val history = viewModel.history.value
        assertEquals(1, history.size)
        assertEquals("help", history[0].input)
        assertTrue(history[0].result is DslResult.Success)
    }

    @Test
    fun shouldPopulateInferredCallWhenLlmResolvesCommand() = runTest {
        fakeResolver.warmUpState = InterpreterState.Ready
        fakeResolver.resolveResult = InterpretResult.Resolved(
            invocation = Invocation("d2.programs.list", listOf("50")),
            inferredCall = "d2.programs.list(50)"
        )
        fakeExecutor.result = DslResult.Success(json = "[]", display = "Programs")
        viewModel = NotebookViewModel(fakeResolver, fakeExecutor)
        advanceUntilIdle()

        viewModel.submit("show me the first 50 programs")
        advanceUntilIdle()

        val entry = viewModel.history.value[0]
        assertEquals("d2.programs.list(50)", entry.inferredCall)
        assertEquals("show me the first 50 programs", entry.input)
    }

    @Test
    fun shouldNotPopulateInferredCallInDslMode() = runTest {
        fakeResolver.warmUpState = InterpreterState.DslFallback
        fakeResolver.resolveResult = InterpretResult.Resolved(
            invocation = Invocation("help", emptyList()),
            inferredCall = null
        )
        fakeExecutor.result = DslResult.Success(json = "{}", display = "ok")
        viewModel = NotebookViewModel(fakeResolver, fakeExecutor)
        advanceUntilIdle()

        viewModel.submit("help")
        advanceUntilIdle()

        assertNull(viewModel.history.value[0].inferredCall)
    }

    @Test
    fun shouldAppendClarificationAsErrorEntry() = runTest {
        fakeResolver.warmUpState = InterpreterState.Ready
        fakeResolver.resolveResult = InterpretResult.Clarification("I cannot map that request.")
        viewModel = NotebookViewModel(fakeResolver, fakeExecutor)
        advanceUntilIdle()

        viewModel.submit("make me a sandwich")
        advanceUntilIdle()

        val entry = viewModel.history.value[0]
        assertTrue(entry.result is DslResult.Error)
        assertEquals("I cannot map that request.", (entry.result as DslResult.Error).message)
        assertNull(entry.inferredCall)
    }

    @Test
    fun shouldAppendFailureAsErrorEntry() = runTest {
        fakeResolver.warmUpState = InterpreterState.Ready
        fakeResolver.resolveResult = InterpretResult.Failure("Parse error: missing paren")
        viewModel = NotebookViewModel(fakeResolver, fakeExecutor)
        advanceUntilIdle()

        viewModel.submit("bad(cmd")
        advanceUntilIdle()

        val entry = viewModel.history.value[0]
        assertTrue(entry.result is DslResult.Error)
        assertTrue((entry.result as DslResult.Error).message.contains("Parse error"))
    }

    @Test
    fun shouldIgnoreBlankInput() = runTest {
        fakeResolver.warmUpState = InterpreterState.Ready
        viewModel = NotebookViewModel(fakeResolver, fakeExecutor)
        advanceUntilIdle()

        viewModel.submit("   ")
        advanceUntilIdle()

        assertTrue(viewModel.history.value.isEmpty())
    }

    @Test
    fun shouldAccumulateMultipleEntries() = runTest {
        fakeResolver.warmUpState = InterpreterState.Ready
        fakeResolver.resolveResult = InterpretResult.Resolved(
            invocation = Invocation("help", emptyList()),
            inferredCall = null
        )
        fakeExecutor.result = DslResult.Success(json = "{}", display = "result")
        viewModel = NotebookViewModel(fakeResolver, fakeExecutor)
        advanceUntilIdle()

        viewModel.submit("help")
        viewModel.submit("commands")
        advanceUntilIdle()

        assertEquals(2, viewModel.history.value.size)
    }

    @Test
    fun shouldNotBeExecutingAfterCompletion() = runTest {
        fakeResolver.warmUpState = InterpreterState.Ready
        fakeResolver.resolveResult = InterpretResult.Resolved(
            invocation = Invocation("help", emptyList()),
            inferredCall = null
        )
        fakeExecutor.result = DslResult.Success(json = "{}", display = "done")
        viewModel = NotebookViewModel(fakeResolver, fakeExecutor)
        advanceUntilIdle()

        viewModel.submit("help")
        advanceUntilIdle()

        assertFalse(viewModel.isExecuting.value)
    }

    @Test
    fun shouldAppendErrorEntryWhenExecutorThrows() = runTest {
        fakeResolver.warmUpState = InterpreterState.Ready
        fakeResolver.resolveResult = InterpretResult.Resolved(
            invocation = Invocation("help", emptyList()),
            inferredCall = null
        )
        fakeExecutor.shouldThrow = true
        viewModel = NotebookViewModel(fakeResolver, fakeExecutor)
        advanceUntilIdle()

        viewModel.submit("help")
        advanceUntilIdle()

        val history = viewModel.history.value
        assertEquals(1, history.size)
        assertTrue(history[0].result is DslResult.Error)
        assertFalse(viewModel.isExecuting.value)
    }
}

private class FakeInputResolver(
    var warmUpState: InterpreterState = InterpreterState.DslFallback,
    var resolveResult: InterpretResult = InterpretResult.Resolved(
        invocation = Invocation("help", emptyList()),
        inferredCall = null
    ),
    override val isLlmPlatform: Boolean = false
) : InputResolver {
    override suspend fun warmUp(): InterpreterState = warmUpState
    override suspend fun resolve(text: String): InterpretResult = resolveResult
}

private class FakeDslExecutor : DslExecutor {
    var result: DslResult = DslResult.Success(json = "{}", display = "default")
    var shouldThrow: Boolean = false
    override val registry: CommandRegistry = CommandRegistry()

    override suspend fun execute(invocation: Invocation): DslResult {
        if (shouldThrow) throw RuntimeException("Executor failure")
        return result
    }
}
