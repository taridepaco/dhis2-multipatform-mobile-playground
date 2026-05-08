package org.dhis2.multiplatformmobileplayground.dsl.commands

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.multiplatformmobileplayground.dsl.executor.CommandHandler
import org.dhis2.multiplatformmobileplayground.dsl.format.jsonString
import org.dhis2.multiplatformmobileplayground.dsl.model.CommandSpec
import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult
import org.dhis2.multiplatformmobileplayground.dsl.model.ParamSpec
import org.hisp.dhis.android.core.D2Manager

private const val DEFAULT_LIMIT = 100

class D2ProgramsListCommand : CommandHandler {
    override val spec = CommandSpec(
        name = "d2.programs.list",
        description = "List programs stored in the local D2 database.",
        parameters = listOf(
            ParamSpec(
                "limit",
                "integer",
                "Maximum number of programs to return (default: $DEFAULT_LIMIT).",
                required = false
            )
        ),
        examples = listOf("d2.programs.list", "d2.programs.list(50)"),
        readOnly = true,
        returns = "JSON array of program objects with id, name, displayName, description."
    )

    override suspend fun execute(args: List<String>): DslResult = withContext(Dispatchers.IO) {
        val limit = args.firstOrNull()?.toIntOrNull() ?: DEFAULT_LIMIT
        val d2 = D2Manager.getD2()
            ?: return@withContext DslResult.Error("D2 instance not available. Please log in first.")
        val programs = d2.programModule().programs().blockingGet().take(limit)
        val json = buildString {
            append("[")
            programs.forEachIndexed { i, p ->
                append("{")
                append("\"id\":${jsonString(p.uid())},")
                append("\"name\":${jsonString(p.name() ?: "")},")
                append("\"displayName\":${jsonString(p.displayName() ?: "")},")
                append("\"description\":${if (p.description() != null) jsonString(p.description()!!) else "null"}")
                append("}")
                if (i < programs.size - 1) append(",")
            }
            append("]")
        }
        val display = if (programs.isEmpty()) {
            "No programs found. Run metadata sync first."
        } else {
            "Programs (${programs.size}):\n" + programs.joinToString("\n") {
                "  ${it.uid()} — ${it.displayName() ?: it.name() ?: ""}"
            }
        }
        DslResult.Success(json = json, display = display)
    }
}
