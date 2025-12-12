package org.dhis2.multiplatformmobileplayground.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.multiplatformmobileplayground.model.LoginCredentials
import org.dhis2.multiplatformmobileplayground.model.LoginResult
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.D2Configuration
import org.hisp.dhis.android.core.D2Manager

class LoginRepositoryImpl(private val context: Context) : LoginRepository {
    
    override suspend fun login(credentials: LoginCredentials): LoginResult = withContext(Dispatchers.IO) {
        try {
            val configuration = D2Configuration.builder()
                .context(context)
                .build()
            
            val d2 = D2Manager.blockingInstantiateD2(configuration)
                ?: throw IllegalStateException("Failed to instantiate D2")
            
            d2.userModule().logIn(
                credentials.username,
                credentials.password,
                credentials.serverUrl
            ).blockingGet()
            
            LoginResult.Success
        } catch (e: Exception) {
            LoginResult.Error(e.message ?: "Unknown error occurred")
        }
    }
}
