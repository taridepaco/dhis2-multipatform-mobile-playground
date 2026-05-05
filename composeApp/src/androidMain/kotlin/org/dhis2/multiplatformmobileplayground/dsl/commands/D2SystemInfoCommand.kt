package org.dhis2.multiplatformmobileplayground.dsl.commands

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.multiplatformmobileplayground.dsl.executor.CommandHandler
import org.dhis2.multiplatformmobileplayground.dsl.format.jsonString
import org.dhis2.multiplatformmobileplayground.dsl.model.CommandSpec
import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult
import org.hisp.dhis.android.core.D2Manager

class D2SystemInfoCommand : CommandHandler {
    override val spec = CommandSpec(
        name = "d2.system.info",
        description = "Fetch system information from the local D2 database.",
        parameters = emptyList(),
        examples = listOf("d2.system.info"),
        readOnly = true,
        returns = "JSON object with contextPath, version, revision, serverDate."
    )

    override suspend fun execute(args: List<String>): DslResult = withContext(Dispatchers.IO) {
        val d2 = D2Manager.getD2()
            ?: return@withContext DslResult.Error("D2 instance not available. Please log in first.")
        val info = d2.systemInfoModule().systemInfo().blockingGet()
            ?: return@withContext DslResult.Error("System info not available. Sync metadata first.")
        val json = buildString {
            append("{")
            append("\"contextPath\":${jsonString(info.contextPath() ?: "")},")
            append("\"version\":${jsonString(info.version() ?: "")},")
            append("\"revision\":${jsonString(info.revision() ?: "")},")
            append("\"serverDate\":${jsonString(info.serverDate()?.toString() ?: "")}")
            append("}")
        }
        val display = buildString {
            appendLine("System Info:")
            appendLine("  Server:   ${info.contextPath() ?: "N/A"}")
            appendLine("  Version:  ${info.version() ?: "N/A"}")
            append("  Revision: ${info.revision() ?: "N/A"}")
        }
        DslResult.Success(json = json, display = display)
    }
}
