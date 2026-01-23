package org.dhis2.multiplatformmobileplayground.model

import androidx.compose.ui.text.input.TextFieldValue

data class LoginUiState(
    val serverUrl: TextFieldValue = TextFieldValue("https://play.im.dhis2.org/stable-2-42-3-1/"),
    val username: TextFieldValue = TextFieldValue("android"),
    val password: TextFieldValue = TextFieldValue("Android123"),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccessful: Boolean = false,
    val userInfo: UserInfo? = null
)
