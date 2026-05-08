package org.dhis2.multiplatformmobileplayground.dsl.executor

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.dhis2.multiplatformmobileplayground.dsl.catalog.CommandRegistry
import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult
import org.dhis2.multiplatformmobileplayground.dsl.model.Invocation

class RegistryDslExecutor(private val registry: CommandRegistry) : DslExecutor {
    override suspend fun execute(invocation: Invocation): DslResult {
        val handler = registry.find(invocation.commandName)
            ?: return DslResult.Error(
                "Unknown command '${invocation.commandName}'. Type 'help' for available commands."
            )
        return try {
            withTimeout(10_000L) {
                handler.execute(invocation.args)
            }
        } catch (e: TimeoutCancellationException) {
            DslResult.Error("Command '${invocation.commandName}' timed out after 10 seconds.")
        } catch (e: Exception) {
            DslResult.Error("Error executing '${invocation.commandName}': ${e.message}")
        }
    }
}
