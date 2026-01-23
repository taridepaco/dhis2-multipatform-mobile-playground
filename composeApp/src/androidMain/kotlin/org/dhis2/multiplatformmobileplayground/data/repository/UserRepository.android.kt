package org.dhis2.multiplatformmobileplayground.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.multiplatformmobileplayground.model.UserInfo
import org.hisp.dhis.android.core.D2Manager

class UserRepositoryImpl(private val context: Context) : UserRepository {
    
    override suspend fun getCurrentUser(): UserInfo? = withContext(Dispatchers.IO) {
        try {
            val d2 = D2Manager.getD2() ?: return@withContext null
            
            val user = d2.userModule().user().blockingGet() ?: return@withContext null
            
            UserInfo(
                username = user.username() ?: "",
                firstName = user.firstName() ?: "",
                serverUrl = d2.systemInfoModule().systemInfo().blockingGet()?.contextPath() ?: ""
            )
        } catch (e: Exception) {
            null
        }
    }
}
