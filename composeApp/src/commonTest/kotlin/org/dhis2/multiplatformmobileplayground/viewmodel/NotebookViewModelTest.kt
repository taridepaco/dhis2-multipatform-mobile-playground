package org.dhis2.multiplatformmobileplayground.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.dhis2.multiplatformmobileplayground.dsl.executor.DslExecutor
import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult
import org.dhis2.multiplatformmobileplayground.dsl.model.Invocation
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotebookViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: NotebookViewModel
    private lateinit var fakeExecutor: FakeDslExecutor

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeExecutor = FakeDslExecutor()
        viewModel = NotebookViewModel(fakeExecutor)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun shouldStartWithEmptyHistory() {
        assertTrue(viewModel.history.value.isEmpty())
        assertFalse(viewModel.isExecuting.value)
    }

    @Test
    fun shouldAppendEntryToHistoryAfterSuccessfulExecution() = runTest {
        fakeExecutor.result = DslResult.Success(json = "{}", display = "ok")
        viewModel.submit("help")
        advanceUntilIdle()

        val history = viewModel.history.value
        assertEquals(1, history.size)
        assertEquals("help", history[0].input)
        assertTrue(history[0].result is DslResult.Success)
    }

    @Test
    fun shouldAppendErrorEntryWhenExecutorReturnsError() = runTest {
        fakeExecutor.result = DslResult.Error("Unknown command")
        viewModel.submit("badcmd")
        advanceUntilIdle()

        val history = viewModel.history.value
        assertEquals(1, history.size)
        assertTrue(history[0].result is DslResult.Error)
        assertEquals("badcmd", history[0].input)
    }

    @Test
    fun shouldAppendParseErrorEntryForMalformedInput() = runTest {
        viewModel.submit("bad(cmd")
        advanceUntilIdle()

        val history = viewModel.history.value
        assertEquals(1, history.size)
        val result = history[0].result
        assertTrue(result is DslResult.Error)
        assertTrue((result as DslResult.Error).message.contains("Parse error"))
    }

    @Test
    fun shouldIgnoreBlankInput() = runTest {
        viewModel.submit("   ")
        advanceUntilIdle()

        assertTrue(viewModel.history.value.isEmpty())
    }

    @Test
    fun shouldAccumulateMultipleEntries() = runTest {
        fakeExecutor.result = DslResult.Success(json = "{}", display = "result")
        viewModel.submit("help")
        viewModel.submit("commands")
        advanceUntilIdle()

        assertEquals(2, viewModel.history.value.size)
        assertEquals("help", viewModel.history.value[0].input)
        assertEquals("commands", viewModel.history.value[1].input)
    }

    @Test
    fun shouldNotBeExecutingAfterCompletion() = runTest {
        fakeExecutor.result = DslResult.Success(json = "{}", display = "done")
        viewModel.submit("help")
        advanceUntilIdle()

        assertFalse(viewModel.isExecuting.value)
    }

    @Test
    fun shouldAppendErrorEntryWhenExecutorThrows() = runTest {
        fakeExecutor.shouldThrow = true
        viewModel.submit("help")
        advanceUntilIdle()

        val history = viewModel.history.value
        assertEquals(1, history.size)
        assertTrue(history[0].result is DslResult.Error)
        assertFalse(viewModel.isExecuting.value)
    }
}

private class FakeDslExecutor : DslExecutor {
    var result: DslResult = DslResult.Success(json = "{}", display = "default")
    var shouldThrow: Boolean = false

    override suspend fun execute(invocation: Invocation): DslResult {
        if (shouldThrow) throw RuntimeException("Executor failure")
        return result
    }
}
