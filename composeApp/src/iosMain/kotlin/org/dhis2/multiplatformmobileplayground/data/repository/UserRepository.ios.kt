package org.dhis2.multiplatformmobileplayground.data.repository

import org.dhis2.multiplatformmobileplayground.model.UserInfo

class UserRepositoryImpl : UserRepository {
    override suspend fun getCurrentUser(): UserInfo? {
        return null
    }
}