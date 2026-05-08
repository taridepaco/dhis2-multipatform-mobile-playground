package org.dhis2.multiplatformmobileplayground.dsl.commands

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.multiplatformmobileplayground.dsl.executor.CommandHandler
import org.dhis2.multiplatformmobileplayground.dsl.format.jsonString
import org.dhis2.multiplatformmobileplayground.dsl.model.CommandSpec
import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult
import org.hisp.dhis.android.core.D2Manager

class D2UsersMeCommand : CommandHandler {
    override val spec = CommandSpec(
        name = "d2.users.me",
        description = "Fetch the currently logged-in user's profile from the local D2 database.",
        parameters = emptyList(),
        examples = listOf("d2.users.me"),
        readOnly = true,
        returns = "JSON object with id, username, firstName, surname."
    )

    override suspend fun execute(args: List<String>): DslResult = withContext(Dispatchers.IO) {
        val d2 = D2Manager.getD2()
            ?: return@withContext DslResult.Error("D2 instance not available. Please log in first.")
        val user = d2.userModule().user().blockingGet()
            ?: return@withContext DslResult.Error("No user found in local database.")
        val json = buildString {
            append("{")
            append("\"id\":${jsonString(user.uid())},")
            append("\"username\":${jsonString(user.username() ?: "")},")
            append("\"firstName\":${jsonString(user.firstName() ?: "")},")
            append("\"surname\":${jsonString(user.surname() ?: "")}")
            append("}")
        }
        val display = "User: ${user.firstName()} ${user.surname()} (${user.username()})"
        DslResult.Success(json = json, display = display)
    }
}
