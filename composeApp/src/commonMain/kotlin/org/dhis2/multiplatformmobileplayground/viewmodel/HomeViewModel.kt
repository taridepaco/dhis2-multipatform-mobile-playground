package org.dhis2.multiplatformmobileplayground.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.dhis2.multiplatformmobileplayground.data.repository.ProgramRepository
import org.dhis2.multiplatformmobileplayground.data.repository.UserRepository
import org.dhis2.multiplatformmobileplayground.model.HomeUiState

class HomeViewModel(
    private val userRepository: UserRepository,
    private val programRepository: ProgramRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        loadUserInfo()
        loadUserPrograms()
    }
    
    private fun loadUserInfo() {
        viewModelScope.launch {
            val userInfo = userRepository.getCurrentUser()
            _uiState.value = HomeUiState(userInfo = userInfo)
        }
    }
    
    private fun loadUserPrograms() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                programRepository.syncPrograms()
                val programs = programRepository.getUserPrograms()
                _uiState.value = _uiState.value.copy(
                    programs = programs,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    isLoading = false
                )
            }
        }
    }
}
