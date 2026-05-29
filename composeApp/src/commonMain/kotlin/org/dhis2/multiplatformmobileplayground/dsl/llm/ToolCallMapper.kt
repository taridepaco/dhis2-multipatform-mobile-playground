package org.dhis2.multiplatformmobileplayground.dsl.llm

import org.dhis2.multiplatformmobileplayground.dsl.catalog.CommandRegistry
import org.dhis2.multiplatformmobileplayground.dsl.model.CommandSpec
import org.dhis2.multiplatformmobileplayground.dsl.model.Invocation

class ToolCallMapper(private val registry: CommandRegistry) {

    fun map(commandName: String, arguments: Map<String, String>): InterpretResult {
        val handler = registry.find(commandName)
            ?: return InterpretResult.Failure("Unknown command: '$commandName'")
        val spec = handler.spec

        val positionalArgs = mutableListOf<String>()
        for (param in spec.parameters) {
            val value = arguments[param.name]
            if (value == null) {
                if (param.required) {
                    return InterpretResult.Clarification(
                        "Missing required parameter '${param.name}' for command '$commandName'."
                    )
                }
                // optional missing param — stop collecting args
                break
            }
            positionalArgs.add(value)
        }

        val invocation = Invocation(commandName = commandName, args = positionalArgs)
        val inferredCall = buildInferredCall(commandName, positionalArgs, spec)
        return InterpretResult.Resolved(invocation = invocation, inferredCall = inferredCall)
    }

    private fun buildInferredCall(name: String, args: List<String>, spec: CommandSpec): String {
        if (args.isEmpty()) return name
        val argsDisplay = spec.parameters.zip(args).joinToString(", ") { (param, value) ->
            if (param.type == "string") "\"$value\"" else value
        }
        return "$name($argsDisplay)"
    }
}
