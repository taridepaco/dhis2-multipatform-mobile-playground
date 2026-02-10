package org.dhis2.multiplatformmobileplayground.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
            _uiState.update { it.copy(userInfo = userInfo) }
        }
    }
    
    private fun loadUserPrograms() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSyncing = true) }
                programRepository.syncPrograms()
                
                _uiState.update { it.copy(isSyncing = false, isLoading = true) }
                val programs = programRepository.getUserPrograms()
                
                _uiState.update { 
                    it.copy(
                        programs = programs,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        error = e.message,
                        isLoading = false,
                        isSyncing = false
                    )
                }
            }
        }
    }
}
