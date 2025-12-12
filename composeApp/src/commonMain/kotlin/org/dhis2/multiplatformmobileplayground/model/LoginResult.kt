package org.dhis2.multiplatformmobileplayground.model

sealed class LoginResult {
    data object Success : LoginResult()
    data class Error(val message: String) : LoginResult()
}
