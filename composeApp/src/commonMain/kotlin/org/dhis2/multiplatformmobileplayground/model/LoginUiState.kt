package org.dhis2.multiplatformmobileplayground.model

import androidx.compose.ui.text.input.TextFieldValue

data class LoginUiState(
    val serverUrl: TextFieldValue = TextFieldValue(""),
    val username: TextFieldValue = TextFieldValue(""),
    val password: TextFieldValue = TextFieldValue(""),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccessful: Boolean = false
)
