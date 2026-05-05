package org.dhis2.multiplatformmobileplayground.dsl.commands

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.multiplatformmobileplayground.dsl.executor.CommandHandler
import org.dhis2.multiplatformmobileplayground.dsl.format.jsonString
import org.dhis2.multiplatformmobileplayground.dsl.model.CommandSpec
import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult
import org.dhis2.multiplatformmobileplayground.dsl.model.ParamSpec
import org.hisp.dhis.android.core.D2Manager

class D2ProgramsGetCommand : CommandHandler {
    override val spec = CommandSpec(
        name = "d2.programs.get",
        description = "Fetch a single program by its UID from the local D2 database.",
        parameters = listOf(
            ParamSpec("uid", "string", "The UID of the program to fetch.", required = true)
        ),
        examples = listOf("d2.programs.get(\"IpHINAT79UW\")"),
        readOnly = true,
        returns = "JSON object with id, name, displayName, description, or null if not found."
    )

    override suspend fun execute(args: List<String>): DslResult = withContext(Dispatchers.IO) {
        val uid = args.firstOrNull()
            ?: return@withContext DslResult.Error("Usage: d2.programs.get(\"<uid>\")")
        val d2 = D2Manager.getD2()
            ?: return@withContext DslResult.Error("D2 instance not available. Please log in first.")
        val program = d2.programModule().programs().uid(uid).blockingGet()
            ?: return@withContext DslResult.Error("Program with UID '$uid' not found in local database.")
        val json = buildString {
            append("{")
            append("\"id\":${jsonString(program.uid())},")
            append("\"name\":${jsonString(program.name() ?: "")},")
            append("\"displayName\":${jsonString(program.displayName() ?: "")},")
            append("\"description\":${if (program.description() != null) jsonString(program.description()!!) else "null"}")
            append("}")
        }
        val display = "Program: ${program.displayName() ?: program.name()} (${program.uid()})"
        DslResult.Success(json = json, display = display)
    }
}
