package org.dhis2.multiplatformmobileplayground.dsl.catalog

import org.dhis2.multiplatformmobileplayground.dsl.executor.CommandHandler
import org.dhis2.multiplatformmobileplayground.dsl.format.jsonString
import org.dhis2.multiplatformmobileplayground.dsl.model.CommandSpec
import org.dhis2.multiplatformmobileplayground.dsl.model.ParamSpec

class CommandRegistry {
    private val handlers = mutableMapOf<String, CommandHandler>()

    fun register(handler: CommandHandler) {
        val name = handler.spec.name
        check(name !in handlers) { "Duplicate command: $name" }
        handlers[name] = handler
    }

    fun find(name: String): CommandHandler? = handlers[name]

    fun allSpecs(): List<CommandSpec> = handlers.values.map { it.spec }.sortedBy { it.name }

    fun toJsonSchema(): String = buildJsonArray(allSpecs())

    fun toJsonSchema(commandName: String): String? {
        val spec = find(commandName)?.spec ?: return null
        return buildJsonArray(listOf(spec))
    }

    private fun buildJsonArray(specs: List<CommandSpec>): String = buildString {
        append("[")
        specs.forEachIndexed { index, spec ->
            append(specToJson(spec))
            if (index < specs.size - 1) append(",")
        }
        append("]")
    }

    private fun specToJson(spec: CommandSpec): String = buildString {
        append("{")
        append("\"name\":${jsonString(spec.name)},")
        append("\"description\":${jsonString(spec.description)},")
        append("\"parameters\":{\"type\":\"object\",\"properties\":{")
        spec.parameters.forEachIndexed { i, p ->
            append(paramToJson(p))
            if (i < spec.parameters.size - 1) append(",")
        }
        append("},\"required\":[")
        val required = spec.parameters.filter { it.required }
        required.forEachIndexed { i, p ->
            append(jsonString(p.name))
            if (i < required.size - 1) append(",")
        }
        append("]},")
        append("\"examples\":[${spec.examples.joinToString(",") { jsonString(it) }}],")
        append("\"readOnly\":${spec.readOnly},")
        append("\"returns\":${jsonString(spec.returns)}")
        append("}")
    }

    private fun paramToJson(p: ParamSpec): String =
        "${jsonString(p.name)}:{\"type\":${jsonString(p.type)},\"description\":${jsonString(p.description)}}"
}
