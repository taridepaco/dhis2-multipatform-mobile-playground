package org.dhis2.multiplatformmobileplayground.dsl.model

sealed class DslResult {
    data class Success(val json: String, val display: String) : DslResult()
    data class Error(val message: String) : DslResult()
}
