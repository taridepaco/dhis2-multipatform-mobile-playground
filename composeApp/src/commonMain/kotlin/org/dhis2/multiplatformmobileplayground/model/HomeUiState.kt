package org.dhis2.multiplatformmobileplayground.model

data class HomeUiState(
    val userInfo: UserInfo? = null,
    val programs: List<Program> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
