package org.dhis2.multiplatformmobileplayground.dsl.commands

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.multiplatformmobileplayground.dsl.executor.CommandHandler
import org.dhis2.multiplatformmobileplayground.dsl.model.CommandSpec
import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult
import org.hisp.dhis.android.core.D2Manager

class D2UsersIsLoggedInCommand : CommandHandler {
    override val spec = CommandSpec(
        name = "d2.users.isLoggedIn",
        description = "Check whether a user is currently logged in to the D2 instance.",
        parameters = emptyList(),
        examples = listOf("d2.users.isLoggedIn"),
        readOnly = true,
        returns = "JSON object with a boolean 'loggedIn' field."
    )

    override suspend fun execute(args: List<String>): DslResult = withContext(Dispatchers.IO) {
        val d2 = D2Manager.getD2()
            ?: return@withContext DslResult.Error("D2 instance not available. Please log in first.")
        val loggedIn = d2.userModule().isLogged().blockingGet() ?: false
        val json = """{"loggedIn":$loggedIn}"""
        val display = if (loggedIn) "Logged in: yes" else "Logged in: no"
        DslResult.Success(json = json, display = display)
    }
}
