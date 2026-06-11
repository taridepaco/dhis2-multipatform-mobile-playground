package org.dhis2.multiplatformmobileplayground.model

import androidx.compose.ui.text.input.TextFieldValue

data class LoginUiState(
    val serverUrl: TextFieldValue = TextFieldValue("https://play.im.dhis2.org/stable-2-42-5"),
    val username: TextFieldValue = TextFieldValue("android"),
    val password: TextFieldValue = TextFieldValue("Android123"),
    val isLoading: Boolean = false,
    val isCheckingAuth: Boolean = true,
    val errorMessage: String? = null,
    val isLoginSuccessful: Boolean = false,
    val userInfo: UserInfo? = null,
    /** Increments on each successful login; used to scope per-session screen state (Home/Notebook). */
    val sessionId: Int = 0
)
