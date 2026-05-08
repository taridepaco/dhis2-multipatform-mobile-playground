package org.dhis2.multiplatformmobileplayground.dsl

import org.dhis2.multiplatformmobileplayground.dsl.catalog.CommandRegistry
import org.dhis2.multiplatformmobileplayground.dsl.executor.CommandHandler
import org.dhis2.multiplatformmobileplayground.dsl.model.CommandSpec
import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult
import org.dhis2.multiplatformmobileplayground.dsl.model.ParamSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandRegistryTest {

    private fun fakeHandler(name: String, params: List<ParamSpec> = emptyList()): CommandHandler =
        object : CommandHandler {
            override val spec = CommandSpec(
                name = name,
                description = "Test command $name.",
                parameters = params,
                examples = listOf("$name example"),
                readOnly = true,
                returns = "Test result."
            )
            override suspend fun execute(args: List<String>): DslResult =
                DslResult.Success(json = "{}", display = "ok")
        }

    @Test
    fun shouldFindRegisteredCommand() {
        val registry = CommandRegistry()
        registry.register(fakeHandler("test.cmd"))
        assertNotNull(registry.find("test.cmd"))
    }

    @Test
    fun shouldReturnNullForUnknownCommand() {
        val registry = CommandRegistry()
        assertNull(registry.find("unknown"))
    }

    @Test
    fun shouldRejectDuplicateCommandName() {
        val registry = CommandRegistry()
        registry.register(fakeHandler("dup"))
        assertFailsWith<IllegalStateException> {
            registry.register(fakeHandler("dup"))
        }
    }

    @Test
    fun shouldReturnAllSpecsSortedByName() {
        val registry = CommandRegistry()
        registry.register(fakeHandler("z.cmd"))
        registry.register(fakeHandler("a.cmd"))
        registry.register(fakeHandler("m.cmd"))
        val specs = registry.allSpecs()
        assertEquals(listOf("a.cmd", "m.cmd", "z.cmd"), specs.map { it.name })
    }

    @Test
    fun shouldExportValidJsonSchema() {
        val registry = CommandRegistry()
        registry.register(
            fakeHandler(
                "d2.test",
                listOf(ParamSpec("uid", "string", "A UID.", required = true))
            )
        )
        val json = registry.toJsonSchema()
        assertTrue(json.startsWith("["))
        assertTrue(json.endsWith("]"))
        assertTrue(json.contains("\"name\":\"d2.test\""))
        assertTrue(json.contains("\"description\""))
        assertTrue(json.contains("\"parameters\""))
        assertTrue(json.contains("\"readOnly\""))
        assertTrue(json.contains("\"returns\""))
        assertTrue(json.contains("\"examples\""))
    }

    @Test
    fun shouldExportSingleCommandSchema() {
        val registry = CommandRegistry()
        registry.register(fakeHandler("cmd.one"))
        registry.register(fakeHandler("cmd.two"))
        val json = registry.toJsonSchema("cmd.one")
        assertNotNull(json)
        assertTrue(json!!.contains("\"name\":\"cmd.one\""))
        assertTrue(!json.contains("\"name\":\"cmd.two\""))
    }

    @Test
    fun shouldReturnNullSchemaForUnknownCommand() {
        val registry = CommandRegistry()
        assertNull(registry.toJsonSchema("nonexistent"))
    }

    @Test
    fun shouldEscapeSpecialCharsInJsonSchema() {
        val registry = CommandRegistry()
        registry.register(
            object : CommandHandler {
                override val spec = CommandSpec(
                    name = "special",
                    description = "Has \"quotes\" and\nnewlines.",
                    parameters = emptyList(),
                    examples = emptyList(),
                    readOnly = true,
                    returns = "nothing"
                )
                override suspend fun execute(args: List<String>) = DslResult.Success("{}", "ok")
            }
        )
        val json = registry.toJsonSchema()
        assertTrue(json.contains("\\\"quotes\\\""))
        assertTrue(json.contains("\\n"))
    }
}
