package org.dhis2.multiplatformmobileplayground.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.multiplatformmobileplayground.model.LoginCredentials
import org.dhis2.multiplatformmobileplayground.model.LoginResult
import org.dhis2.multiplatformmobileplayground.model.UserInfo
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.D2Configuration
import org.hisp.dhis.android.core.D2Manager

class LoginRepositoryImpl(private val context: Context) : LoginRepository {

    private fun initializeD2(): D2 {
        return try {
            D2Manager.getD2()
        } catch (e: Exception) {
            val configuration = D2Configuration.builder()
                .context(context)
                .build()
            D2Manager.blockingInstantiateD2(configuration)
                ?: throw IllegalStateException("Failed to instantiate D2")
        }
    }

    override suspend fun isUserLoggedIn(): Boolean = withContext(Dispatchers.IO) {
        try {
            val d2 = initializeD2()
            d2.userModule().isLogged().blockingGet() ?: false
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun login(credentials: LoginCredentials): LoginResult = withContext(Dispatchers.IO) {
        try {
            val d2 = D2Manager.getD2()
                ?: throw IllegalStateException("D2 is not initialized. Call isUserLoggedIn() first.")

            val user = d2.userModule().logIn(
                credentials.username,
                credentials.password,
                credentials.serverUrl
            ).blockingGet()

            val userInfo = UserInfo(
                username = credentials.username,
                firstName = user.firstName() ?: "",
                serverUrl = credentials.serverUrl
            )

            LoginResult.Success(userInfo)
        } catch (e: Exception) {
            LoginResult.Error(e.message ?: "Unknown error occurred")
        }
    }
}
