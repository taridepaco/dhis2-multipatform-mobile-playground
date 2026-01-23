package org.dhis2.multiplatformmobileplayground.data.repository

import org.dhis2.multiplatformmobileplayground.model.UserInfo

interface UserRepository {
    suspend fun getCurrentUser(): UserInfo?
}
