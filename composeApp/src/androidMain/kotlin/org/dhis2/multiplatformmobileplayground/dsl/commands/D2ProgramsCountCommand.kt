package org.dhis2.multiplatformmobileplayground.dsl.commands

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.multiplatformmobileplayground.dsl.executor.CommandHandler
import org.dhis2.multiplatformmobileplayground.dsl.model.CommandSpec
import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult
import org.hisp.dhis.android.core.D2Manager

class D2ProgramsCountCommand : CommandHandler {
    override val spec = CommandSpec(
        name = "d2.programs.count",
        description = "Count the total number of programs stored in the local D2 database.",
        parameters = emptyList(),
        examples = listOf("d2.programs.count"),
        readOnly = true,
        returns = "JSON object with an integer 'count' field."
    )

    override suspend fun execute(args: List<String>): DslResult = withContext(Dispatchers.IO) {
        val d2 = D2Manager.getD2()
            ?: return@withContext DslResult.Error("D2 instance not available. Please log in first.")
        val count = d2.programModule().programs().blockingCount()
        DslResult.Success(json = """{"count":$count}""", display = "Program count: $count")
    }
}
