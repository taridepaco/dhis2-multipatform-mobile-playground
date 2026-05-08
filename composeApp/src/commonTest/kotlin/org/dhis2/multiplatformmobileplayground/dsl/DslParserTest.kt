package org.dhis2.multiplatformmobileplayground.dsl

import org.dhis2.multiplatformmobileplayground.dsl.parser.DslParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DslParserTest {

    @Test
    fun shouldParseSimpleCommandWithNoArgs() {
        val inv = DslParser.parse("help")
        assertEquals("help", inv.commandName)
        assertEquals(emptyList(), inv.args)
    }

    @Test
    fun shouldParseCommandWithLeadingAndTrailingWhitespace() {
        val inv = DslParser.parse("  commands  ")
        assertEquals("commands", inv.commandName)
        assertEquals(emptyList(), inv.args)
    }

    @Test
    fun shouldParseDottedCommandWithNoArgs() {
        val inv = DslParser.parse("d2.users.me")
        assertEquals("d2.users.me", inv.commandName)
        assertEquals(emptyList(), inv.args)
    }

    @Test
    fun shouldParseCommandWithEmptyParentheses() {
        val inv = DslParser.parse("d2.programs.list()")
        assertEquals("d2.programs.list", inv.commandName)
        assertEquals(emptyList(), inv.args)
    }

    @Test
    fun shouldParseCommandWithSingleQuotedArg() {
        val inv = DslParser.parse("d2.programs.get(\"IpHINAT79UW\")")
        assertEquals("d2.programs.get", inv.commandName)
        assertEquals(listOf("IpHINAT79UW"), inv.args)
    }

    @Test
    fun shouldParseCommandWithNumericArg() {
        val inv = DslParser.parse("d2.programs.list(50)")
        assertEquals("d2.programs.list", inv.commandName)
        assertEquals(listOf("50"), inv.args)
    }

    @Test
    fun shouldParseSpaceSeparatedCommandWithArg() {
        val inv = DslParser.parse("describe d2.programs.get")
        assertEquals("describe", inv.commandName)
        assertEquals(listOf("d2.programs.get"), inv.args)
    }

    @Test
    fun shouldParseSpaceSeparatedArgWithSpaces() {
        val inv = DslParser.parse("describe  d2.users.me")
        assertEquals("describe", inv.commandName)
        assertEquals(listOf("d2.users.me"), inv.args)
    }

    @Test
    fun shouldRejectEmptyInput() {
        assertFailsWith<IllegalArgumentException> {
            DslParser.parse("")
        }
    }

    @Test
    fun shouldRejectBlankInput() {
        assertFailsWith<IllegalArgumentException> {
            DslParser.parse("   ")
        }
    }

    @Test
    fun shouldRejectMissingClosingParenthesis() {
        assertFailsWith<IllegalArgumentException> {
            DslParser.parse("d2.programs.get(\"uid\"")
        }
    }

    @Test
    fun shouldParseMultipleQuotedArgs() {
        val inv = DslParser.parse("someCmd(\"arg1\", \"arg2\")")
        assertEquals("someCmd", inv.commandName)
        assertEquals(listOf("arg1", "arg2"), inv.args)
    }
}
