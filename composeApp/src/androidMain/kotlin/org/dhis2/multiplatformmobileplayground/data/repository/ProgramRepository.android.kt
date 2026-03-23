package org.dhis2.multiplatformmobileplayground.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.multiplatformmobileplayground.model.Program
import org.hisp.dhis.android.core.D2Manager

class ProgramRepositoryImpl(private val context: Context) : ProgramRepository {
    
    override suspend fun getUserPrograms(): List<Program> = withContext(Dispatchers.IO) {
        try {
            val d2 = D2Manager.getD2() ?: return@withContext emptyList()
            
            val programs = d2.programModule().programs().blockingGet()
            
            programs.map { program ->
                Program(
                    id = program.uid(),
                    name = program.name() ?: "",
                    displayName = program.displayName() ?: "",
                    description = program.description()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun syncPrograms() = withContext(Dispatchers.IO) {
        try {
            val d2 = D2Manager.getD2() ?: return@withContext
            d2.metadataModule().blockingDownload()
        } catch (e: Exception) {
            // Handle error or log it
            e.printStackTrace()
        }
    }

    override suspend fun hasMetadata(): Boolean = withContext(Dispatchers.IO) {
        try {
            val d2 = D2Manager.getD2() ?: return@withContext false
            return@withContext d2.programModule().programs().blockingCount() > 0
        } catch (e: Exception) {
            return@withContext false
        }
    }
}
