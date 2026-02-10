package org.dhis2.multiplatformmobileplayground.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.dhis2.multiplatformmobileplayground.data.repository.ProgramRepository
import org.dhis2.multiplatformmobileplayground.data.repository.UserRepository
import org.dhis2.multiplatformmobileplayground.model.HomeUiState
import org.dhis2.multiplatformmobileplayground.model.Program
import org.dhis2.multiplatformmobileplayground.model.UserInfo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: HomeViewModel
    private lateinit var fakeUserRepository: FakeUserRepository
    private lateinit var fakeProgramRepository: FakeProgramRepository
    
    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeUserRepository = FakeUserRepository()
        fakeProgramRepository = FakeProgramRepository()
    }
    
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun shouldLoadUserInfoOnInit() = runTest {
        val expectedUserInfo = UserInfo("testuser", "Test", "https://test.org")
        fakeUserRepository.userInfo = expectedUserInfo
        viewModel = HomeViewModel(fakeUserRepository, fakeProgramRepository)
        
        advanceUntilIdle()
        
        val uiState = viewModel.uiState.value
        assertNotNull(uiState.userInfo)
        assertEquals(expectedUserInfo.username, uiState.userInfo?.username)
        assertEquals(expectedUserInfo.firstName, uiState.userInfo?.firstName)
        assertEquals(expectedUserInfo.serverUrl, uiState.userInfo?.serverUrl)
    }
    
    @Test
    fun shouldLoadProgramsOnInit() = runTest {
        val expectedPrograms = listOf(
            Program("program1", "Program 1", "Display Program 1", "Description 1"),
            Program("program2", "Program 2", "Display Program 2", "Description 2")
        )
        fakeProgramRepository.programs = expectedPrograms
        viewModel = HomeViewModel(fakeUserRepository, fakeProgramRepository)
        
        advanceUntilIdle()
        
        val uiState = viewModel.uiState.value
        assertEquals(expectedPrograms.size, uiState.programs.size)
        assertEquals(expectedPrograms[0].id, uiState.programs[0].id)
        assertEquals(expectedPrograms[1].name, uiState.programs[1].name)
    }
    
    @Test
    fun shouldSetLoadingStateWhenLoadingPrograms() = runTest {
        fakeProgramRepository.executionDelay = 1000
        viewModel = HomeViewModel(fakeUserRepository, fakeProgramRepository)
        
        // Execute scheduled tasks (launch blocks)
        runCurrent()
        
        // At this point, coroutine should be running and suspended at sync call
        // We expect isSyncing to be true, isLoading to be false (as sync happens first)
        assertTrue(viewModel.uiState.value.isSyncing)
        assertFalse(viewModel.uiState.value.isLoading)
        
        // Since we have two delays (sync + get), total delay is 2000ms.
        // Let's advance 1000ms (sync done)
        advanceTimeBy(1001)
        runCurrent()
        
        // Now sync should be done, and we should be fetching programs (isLoading = true)
        assertFalse(viewModel.uiState.value.isSyncing)
        assertTrue(viewModel.uiState.value.isLoading)
        
        // Advance remaining time
        advanceUntilIdle()
        
        // After loading completes, loading should be false
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isSyncing)
    }
    
    @Test
    fun shouldHandleEmptyProgramsList() = runTest {
        fakeProgramRepository.programs = emptyList()
        viewModel = HomeViewModel(fakeUserRepository, fakeProgramRepository)
        
        advanceUntilIdle()
        
        val uiState = viewModel.uiState.value
        assertTrue(uiState.programs.isEmpty())
        assertFalse(uiState.isLoading)
        assertNull(uiState.error)
    }
    
    @Test
    fun shouldHandleErrorWhenLoadingPrograms() = runTest {
        fakeProgramRepository.shouldThrowError = true
        viewModel = HomeViewModel(fakeUserRepository, fakeProgramRepository)
        
        advanceUntilIdle()
        
        val uiState = viewModel.uiState.value
        assertNotNull(uiState.error)
        assertFalse(uiState.isLoading)
    }
    
    @Test
    fun shouldHandleNullUserInfo() = runTest {
        fakeUserRepository.userInfo = null
        viewModel = HomeViewModel(fakeUserRepository, fakeProgramRepository)
        
        advanceUntilIdle()
        
        val uiState = viewModel.uiState.value
        assertNull(uiState.userInfo)
        assertFalse(uiState.isLoading)
    }
}

class FakeUserRepository : UserRepository {
    var userInfo: UserInfo? = UserInfo("testuser", "Test", "https://test.org")
    
    override suspend fun getCurrentUser(): UserInfo? {
        return userInfo
    }
}

class FakeProgramRepository : ProgramRepository {
    var programs: List<Program> = listOf(
        Program("program1", "Program 1", "Display Program 1", "Description 1")
    )
    var shouldThrowError: Boolean = false
    var executionDelay: Long = 0
    
    override suspend fun getUserPrograms(): List<Program> {
        if (executionDelay > 0) {
            kotlinx.coroutines.delay(executionDelay)
        }
        if (shouldThrowError) {
            throw Exception("Failed to load programs")
        }
        return programs
    }

    override suspend fun syncPrograms() {
        if (executionDelay > 0) {
            kotlinx.coroutines.delay(executionDelay)
        }
        // No-op for fake
    }
}