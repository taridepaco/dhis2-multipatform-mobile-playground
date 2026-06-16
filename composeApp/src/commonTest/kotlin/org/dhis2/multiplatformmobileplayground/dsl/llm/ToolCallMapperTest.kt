package org.dhis2.multiplatformmobileplayground.dsl.llm

import org.dhis2.multiplatformmobileplayground.dsl.catalog.CommandRegistry
import org.dhis2.multiplatformmobileplayground.dsl.executor.CommandHandler
import org.dhis2.multiplatformmobileplayground.dsl.model.CommandSpec
import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult
import org.dhis2.multiplatformmobileplayground.dsl.model.ParamSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ToolCallMapperTest {

    private val registry = CommandRegistry().also { reg ->
        reg.register(noArgCommand("d2.users.me"))
        reg.register(intArgCommand("d2.programs.list", "count", required = false))
        reg.register(stringArgCommand("d2.programs.get", "uid", required = true))
        reg.register(boolArgCommand("d2.debug.toggle", "enabled", required = true))
    }
    private val mapper = ToolCallMapper(registry)

    @Test
    fun shouldResolveNoArgCommandWithEmptyArgs() {
        val result = mapper.map("d2.users.me", emptyMap())
        val resolved = assertIs<InterpretResult.Resolved>(result)
        assertEquals("d2.users.me", resolved.invocation.commandName)
        assertEquals(emptyList(), resolved.invocation.args)
        assertEquals("d2.users.me", resolved.inferredCall)
    }

    @Test
    fun shouldResolveIntArgAndFormatWithoutQuotes() {
        val result = mapper.map("d2.programs.list", mapOf("count" to "50"))
        val resolved = assertIs<InterpretResult.Resolved>(result)
        assertEquals(listOf("50"), resolved.invocation.args)
        assertEquals("d2.programs.list(50)", resolved.inferredCall)
    }

    @Test
    fun shouldResolveStringArgAndFormatWithQuotes() {
        val result = mapper.map("d2.programs.get", mapOf("uid" to "IpHINAT79UW"))
        val resolved = assertIs<InterpretResult.Resolved>(result)
        assertEquals(listOf("IpHINAT79UW"), resolved.invocation.args)
        assertEquals("d2.programs.get(\"IpHINAT79UW\")", resolved.inferredCall)
    }

    @Test
    fun shouldResolveBoolArgAndFormatWithoutQuotes() {
        val result = mapper.map("d2.debug.toggle", mapOf("enabled" to "true"))
        val resolved = assertIs<InterpretResult.Resolved>(result)
        assertEquals(listOf("true"), resolved.invocation.args)
        assertEquals("d2.debug.toggle(true)", resolved.inferredCall)
    }

    @Test
    fun shouldReturnClarificationWhenRequiredArgMissing() {
        val result = mapper.map("d2.programs.get", emptyMap())
        val clarification = assertIs<InterpretResult.Clarification>(result)
        assert(clarification.message.contains("uid"))
    }

    @Test
    fun shouldReturnFailureForUnknownCommand() {
        val result = mapper.map("d2.nonexistent.command", emptyMap())
        assertIs<InterpretResult.Failure>(result)
    }

    @Test
    fun shouldResolveOptionalArgAsEmptyWhenMissing() {
        val result = mapper.map("d2.programs.list", emptyMap())
        val resolved = assertIs<InterpretResult.Resolved>(result)
        assertEquals(emptyList(), resolved.invocation.args)
        assertEquals("d2.programs.list", resolved.inferredCall)
    }

    @Test
    fun shouldStoreStringArgWithoutQuotesInInvocationArgs() {
        val result = mapper.map("d2.programs.get", mapOf("uid" to "abc123"))
        val resolved = assertIs<InterpretResult.Resolved>(result)
        // args stored without quotes (matching DslParser convention)
        assertEquals("abc123", resolved.invocation.args[0])
    }
}

private fun noArgCommand(name: String): CommandHandler = object : CommandHandler {
    override val spec = CommandSpec(name, "Test", emptyList(), emptyList(), true, "void")
    override suspend fun execute(args: List<String>): DslResult = DslResult.Success("{}", "ok")
}

private fun intArgCommand(name: String, paramName: String, required: Boolean): CommandHandler =
    object : CommandHandler {
        override val spec = CommandSpec(
            name, "Test",
            listOf(ParamSpec(paramName, "integer", "count", required)),
            emptyList(), true, "list"
        )
        override suspend fun execute(args: List<String>): DslResult = DslResult.Success("[]", "ok")
    }

private fun stringArgCommand(name: String, paramName: String, required: Boolean): CommandHandler =
    object : CommandHandler {
        override val spec = CommandSpec(
            name, "Test",
            listOf(ParamSpec(paramName, "string", "uid", required)),
            emptyList(), true, "object"
        )
        override suspend fun execute(args: List<String>): DslResult = DslResult.Success("{}", "ok")
    }

private fun boolArgCommand(name: String, paramName: String, required: Boolean): CommandHandler =
    object : CommandHandler {
        override val spec = CommandSpec(
            name, "Test",
            listOf(ParamSpec(paramName, "boolean", "flag", required)),
            emptyList(), true, "void"
        )
        override suspend fun execute(args: List<String>): DslResult = DslResult.Success("{}", "ok")
    }
