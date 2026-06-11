package org.dhis2.multiplatformmobileplayground.data.repository

import org.dhis2.multiplatformmobileplayground.model.LoginCredentials
import org.dhis2.multiplatformmobileplayground.model.LoginResult

interface LoginRepository {
    suspend fun login(credentials: LoginCredentials): LoginResult
    suspend fun isUserLoggedIn(): Boolean

    /**
     * Logs out the current DHIS2 session. The local database (and the downloaded LLM model in
     * app storage) are left intact, so logging back into the same server/user reopens it.
     */
    suspend fun logout()
}