package org.dhis2.multiplatformmobileplayground.data.repository

import org.dhis2.multiplatformmobileplayground.model.Program

class ProgramRepositoryImpl : ProgramRepository {
    override suspend fun getUserPrograms(): List<Program> {
        return emptyList()
    }

    override suspend fun syncPrograms() {
        // No-op for iOS
    }

    override suspend fun hasMetadata(): Boolean {
        return false
    }
}
