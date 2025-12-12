package org.dhis2.multiplatformmobileplayground.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.dhis2.multiplatformmobileplayground.data.repository.LoginRepository
import org.dhis2.multiplatformmobileplayground.model.LoginCredentials
import org.dhis2.multiplatformmobileplayground.model.LoginResult
import org.dhis2.multiplatformmobileplayground.model.LoginUiState

class LoginViewModel(private val loginRepository: LoginRepository) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    
    fun onServerUrlChanged(serverUrl: String) {
        _uiState.update { it.copy(serverUrl = serverUrl, errorMessage = null) }
    }
    
    fun onUsernameChanged(username: String) {
        _uiState.update { it.copy(username = username, errorMessage = null) }
    }
    
    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }
    
    fun onLoginClicked() {
        val currentState = _uiState.value
        
        if (currentState.serverUrl.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Server URL is required") }
            return
        }
        
        if (currentState.username.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Username is required") }
            return
        }
        
        if (currentState.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Password is required") }
            return
        }
        
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        
        viewModelScope.launch {
            val credentials = LoginCredentials(
                serverUrl = currentState.serverUrl,
                username = currentState.username,
                password = currentState.password
            )
            
            when (val result = loginRepository.login(credentials)) {
                is LoginResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            isLoginSuccessful = true,
                            errorMessage = null
                        )
                    }
                }
                is LoginResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
