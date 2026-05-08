package org.dhis2.multiplatformmobileplayground.dsl.catalog

import org.dhis2.multiplatformmobileplayground.dsl.executor.CommandHandler
import org.dhis2.multiplatformmobileplayground.dsl.model.CommandSpec
import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult
import org.dhis2.multiplatformmobileplayground.dsl.model.ParamSpec

class HelpCommand(private val registry: CommandRegistry) : CommandHandler {
    override val spec = CommandSpec(
        name = "help",
        description = "List all available commands with a short description.",
        parameters = emptyList(),
        examples = listOf("help"),
        readOnly = true,
        returns = "A formatted list of command names and one-line descriptions."
    )

    override suspend fun execute(args: List<String>): DslResult {
        val lines = registry.allSpecs().joinToString("\n") { "  ${it.name} — ${it.description}" }
        val display = "Available commands:\n$lines"
        return DslResult.Success(json = registry.toJsonSchema(), display = display)
    }
}

class DescribeCommand(private val registry: CommandRegistry) : CommandHandler {
    override val spec = CommandSpec(
        name = "describe",
        description = "Show the full spec of a single command, including parameters and examples.",
        parameters = listOf(
            ParamSpec("command", "string", "The command name to describe.", required = true)
        ),
        examples = listOf("describe d2.programs.get", "describe d2.users.me"),
        readOnly = true,
        returns = "Detailed spec for the requested command: parameters, examples, return type."
    )

    override suspend fun execute(args: List<String>): DslResult {
        val commandName = args.firstOrNull()?.trim()
            ?: return DslResult.Error("Usage: describe <commandName>")
        val json = registry.toJsonSchema(commandName)
            ?: return DslResult.Error("Unknown command: '$commandName'. Type 'help' for a list.")
        val spec = registry.find(commandName)!!.spec
        val display = buildString {
            appendLine("Command: ${spec.name}")
            appendLine("Description: ${spec.description}")
            appendLine("Read-only: ${spec.readOnly}")
            appendLine("Returns: ${spec.returns}")
            if (spec.parameters.isNotEmpty()) {
                appendLine("Parameters:")
                spec.parameters.forEach { p ->
                    val req = if (p.required) " [required]" else ""
                    appendLine("  ${p.name} (${p.type})$req: ${p.description}")
                }
            } else {
                appendLine("Parameters: none")
            }
            if (spec.examples.isNotEmpty()) {
                appendLine("Examples:")
                spec.examples.forEach { e -> appendLine("  $e") }
            }
        }
        return DslResult.Success(json = json, display = display.trimEnd())
    }
}

class CommandsCatalogCommand(private val registry: CommandRegistry) : CommandHandler {
    override val spec = CommandSpec(
        name = "commands",
        description = "Output the full command catalog as a JSON schema consumable by an LLM agent.",
        parameters = emptyList(),
        examples = listOf("commands"),
        readOnly = true,
        returns = "JSON array of all command specs with name, description, parameters, examples, readOnly, returns."
    )

    override suspend fun execute(args: List<String>): DslResult {
        val json = registry.toJsonSchema()
        return DslResult.Success(json = json, display = json)
    }
}
