package org.dhis2.multiplatformmobileplayground.viewmodel

import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.dhis2.multiplatformmobileplayground.data.repository.LoginRepository
import org.dhis2.multiplatformmobileplayground.model.LoginCredentials
import org.dhis2.multiplatformmobileplayground.model.LoginResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: LoginViewModel
    private lateinit var fakeRepository: FakeLoginRepository
    
    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeLoginRepository()
        viewModel = LoginViewModel(fakeRepository)
    }
    
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun shouldUpdateServerUrlWhenChanged() {
        val testUrl = "https://play.dhis2.org/2.40.0"
        
        viewModel.onServerUrlChanged(TextFieldValue(testUrl))
        
        assertEquals(testUrl, viewModel.uiState.value.serverUrl.text)
    }
    
    @Test
    fun shouldUpdateUsernameWhenChanged() {
        val testUsername = "admin"
        
        viewModel.onUsernameChanged(TextFieldValue(testUsername))
        
        assertEquals(testUsername, viewModel.uiState.value.username.text)
    }
    
    @Test
    fun shouldUpdatePasswordWhenChanged() {
        val testPassword = "district"
        
        viewModel.onPasswordChanged(TextFieldValue(testPassword))
        
        assertEquals(testPassword, viewModel.uiState.value.password.text)
    }
    
    @Test
    fun shouldShowErrorWhenServerUrlIsEmpty() {
        viewModel.onUsernameChanged(TextFieldValue("admin"))
        viewModel.onPasswordChanged(TextFieldValue("district"))
        
        viewModel.onLoginClicked()
        
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals("Server URL is required", viewModel.uiState.value.errorMessage)
    }
    
    @Test
    fun shouldShowErrorWhenUsernameIsEmpty() {
        viewModel.onServerUrlChanged(TextFieldValue("https://play.dhis2.org/2.40.0"))
        viewModel.onPasswordChanged(TextFieldValue("district"))
        
        viewModel.onLoginClicked()
        
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals("Username is required", viewModel.uiState.value.errorMessage)
    }
    
    @Test
    fun shouldShowErrorWhenPasswordIsEmpty() {
        viewModel.onServerUrlChanged(TextFieldValue("https://play.dhis2.org/2.40.0"))
        viewModel.onUsernameChanged(TextFieldValue("admin"))
        
        viewModel.onLoginClicked()
        
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals("Password is required", viewModel.uiState.value.errorMessage)
    }
    
    @Test
    fun shouldLoginSuccessfullyWhenCredentialsAreValid() = runTest {
        fakeRepository.loginResult = LoginResult.Success
        viewModel.onServerUrlChanged(TextFieldValue("https://play.dhis2.org/2.40.0"))
        viewModel.onUsernameChanged(TextFieldValue("admin"))
        viewModel.onPasswordChanged(TextFieldValue("district"))
        
        viewModel.onLoginClicked()
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.isLoginSuccessful)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }
    
    @Test
    fun shouldShowErrorWhenLoginFails() = runTest {
        val errorMessage = "Invalid credentials"
        fakeRepository.loginResult = LoginResult.Error(errorMessage)
        viewModel.onServerUrlChanged(TextFieldValue("https://play.dhis2.org/2.40.0"))
        viewModel.onUsernameChanged(TextFieldValue("admin"))
        viewModel.onPasswordChanged(TextFieldValue("wrong"))
        
        viewModel.onLoginClicked()
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isLoginSuccessful)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(errorMessage, viewModel.uiState.value.errorMessage)
    }
    
    @Test
    fun shouldSetLoadingStateDuringLogin() = runTest {
        fakeRepository.loginResult = LoginResult.Success
        viewModel.onServerUrlChanged(TextFieldValue("https://play.dhis2.org/2.40.0"))
        viewModel.onUsernameChanged(TextFieldValue("admin"))
        viewModel.onPasswordChanged(TextFieldValue("district"))
        
        viewModel.onLoginClicked()
        
        assertTrue(viewModel.uiState.value.isLoading)
    }
    
    @Test
    fun shouldClearErrorWhenClearErrorIsCalled() {
        viewModel.onLoginClicked()
        assertNotNull(viewModel.uiState.value.errorMessage)
        
        viewModel.clearError()
        
        assertNull(viewModel.uiState.value.errorMessage)
    }
    
    @Test
    fun shouldClearErrorWhenInputChanges() {
        viewModel.onLoginClicked()
        assertNotNull(viewModel.uiState.value.errorMessage)
        
        viewModel.onServerUrlChanged(TextFieldValue("https://play.dhis2.org/2.40.0"))
        
        assertNull(viewModel.uiState.value.errorMessage)
    }
}

class FakeLoginRepository : LoginRepository {
    var loginResult: LoginResult = LoginResult.Success
    
    override suspend fun login(credentials: LoginCredentials): LoginResult {
        return loginResult
    }
}
