package org.dhis2.multiplatformmobileplayground.data.repository

import org.dhis2.multiplatformmobileplayground.model.Program

interface ProgramRepository {
    suspend fun getUserPrograms(): List<Program>
    suspend fun syncPrograms()
    suspend fun hasMetadata(): Boolean
}
