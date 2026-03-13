package org.dhis2.multiplatformmobileplayground.model

import androidx.compose.ui.text.input.TextFieldValue

data class LoginUiState(
    val serverUrl: TextFieldValue = TextFieldValue("https://android.im.dhis2.org/current"),
    val username: TextFieldValue = TextFieldValue("android"),
    val password: TextFieldValue = TextFieldValue("Android123"),
    val isLoading: Boolean = false,
    val isCheckingAuth: Boolean = true,
    val errorMessage: String? = null,
    val isLoginSuccessful: Boolean = false,
    val userInfo: UserInfo? = null
)
