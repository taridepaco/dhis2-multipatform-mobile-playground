package org.dhis2.multiplatformmobileplayground.dsl.format

import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult

object ResultFormatter {
    fun formatForDisplay(result: DslResult): String = when (result) {
        is DslResult.Success -> result.display
        is DslResult.Error -> "Error: ${result.message}"
    }

    fun formatAsJson(result: DslResult): String = when (result) {
        is DslResult.Success -> result.json
        is DslResult.Error -> """{"error":${jsonString(result.message)}}"""
    }
}
