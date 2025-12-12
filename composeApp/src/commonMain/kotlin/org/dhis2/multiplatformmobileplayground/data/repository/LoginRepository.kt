package org.dhis2.multiplatformmobileplayground.data.repository

import org.dhis2.multiplatformmobileplayground.model.LoginCredentials
import org.dhis2.multiplatformmobileplayground.model.LoginResult

interface LoginRepository {
    suspend fun login(credentials: LoginCredentials): LoginResult
}