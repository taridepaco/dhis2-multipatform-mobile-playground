package org.dhis2.multiplatformmobileplayground.data.repository

import org.dhis2.multiplatformmobileplayground.model.LoginCredentials
import org.dhis2.multiplatformmobileplayground.model.LoginResult

class LoginRepositoryImpl : LoginRepository {
    override suspend fun login(credentials: LoginCredentials): LoginResult {
        return LoginResult.Error("DHIS2 SDK is not available on iOS platform")
    }
}
