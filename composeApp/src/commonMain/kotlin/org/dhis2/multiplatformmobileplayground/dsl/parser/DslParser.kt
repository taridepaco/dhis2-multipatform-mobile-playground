package org.dhis2.multiplatformmobileplayground.dsl.parser

import org.dhis2.multiplatformmobileplayground.dsl.model.Invocation

object DslParser {
    fun parse(input: String): Invocation {
        val trimmed = input.trim()
        require(trimmed.isNotEmpty()) { "Input must not be empty." }
        return if (trimmed.contains('(')) parseCallForm(trimmed) else parseSpaceForm(trimmed)
    }

    private fun parseCallForm(input: String): Invocation {
        val parenIdx = input.indexOf('(')
        val name = input.substring(0, parenIdx).trim()
        require(name.isNotEmpty()) { "Command name must not be empty." }
        require(input.endsWith(')')) { "Missing closing parenthesis in: $input" }
        val argsStr = input.substring(parenIdx + 1, input.length - 1).trim()
        val args = if (argsStr.isEmpty()) emptyList() else parseArgs(argsStr)
        return Invocation(commandName = name, args = args)
    }

    private fun parseSpaceForm(input: String): Invocation {
        val spaceIdx = input.indexOf(' ')
        return if (spaceIdx < 0) {
            Invocation(commandName = input, args = emptyList())
        } else {
            val name = input.substring(0, spaceIdx)
            val arg = input.substring(spaceIdx + 1).trim()
            Invocation(commandName = name, args = if (arg.isEmpty()) emptyList() else listOf(arg))
        }
    }

    private fun parseArgs(argsStr: String): List<String> {
        val args = mutableListOf<String>()
        var i = 0
        while (i < argsStr.length) {
            while (i < argsStr.length && argsStr[i] == ' ') i++
            if (i >= argsStr.length) break

            if (argsStr[i] == '"') {
                i++ // skip opening quote
                val start = i
                while (i < argsStr.length && argsStr[i] != '"') i++
                args.add(argsStr.substring(start, i))
                if (i < argsStr.length) i++ // skip closing quote
            } else {
                val start = i
                while (i < argsStr.length && argsStr[i] != ',') i++
                val raw = argsStr.substring(start, i).trim()
                if (raw.isNotEmpty()) args.add(raw)
            }

            while (i < argsStr.length && (argsStr[i] == ' ' || argsStr[i] == ',')) i++
        }
        return args
    }
}
