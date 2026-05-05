package org.dhis2.multiplatformmobileplayground.dsl

import org.dhis2.multiplatformmobileplayground.dsl.format.ResultFormatter
import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultFormatterTest {

    @Test
    fun shouldFormatSuccessForDisplay() {
        val result = DslResult.Success(json = "{\"id\":\"123\"}", display = "User: Alice")
        assertEquals("User: Alice", ResultFormatter.formatForDisplay(result))
    }

    @Test
    fun shouldFormatErrorForDisplay() {
        val result = DslResult.Error("Something went wrong")
        assertEquals("Error: Something went wrong", ResultFormatter.formatForDisplay(result))
    }

    @Test
    fun shouldFormatSuccessAsJson() {
        val json = "{\"id\":\"abc\"}"
        val result = DslResult.Success(json = json, display = "irrelevant")
        assertEquals(json, ResultFormatter.formatAsJson(result))
    }

    @Test
    fun shouldFormatErrorAsJson() {
        val result = DslResult.Error("Command failed")
        val json = ResultFormatter.formatAsJson(result)
        assertTrue(json.startsWith("{"))
        assertTrue(json.contains("\"error\""))
        assertTrue(json.contains("Command failed"))
    }

    @Test
    fun shouldEscapeQuotesInErrorJson() {
        val result = DslResult.Error("Has \"quotes\"")
        val json = ResultFormatter.formatAsJson(result)
        assertTrue(json.contains("\\\"quotes\\\""))
    }
}
