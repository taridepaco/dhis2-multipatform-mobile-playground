package org.dhis2.multiplatformmobileplayground.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.dhis2.multiplatformmobileplayground.model.HomeUiState
import org.dhis2.multiplatformmobileplayground.model.UserInfo

class HomeViewModel(userInfo: UserInfo) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState(userInfo = userInfo))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
}
