package org.dhis2.multiplatformmobileplayground.model

sealed class LoginResult {
    data class Success(val userInfo: UserInfo) : LoginResult()
    data class Error(val message: String) : LoginResult()
}
