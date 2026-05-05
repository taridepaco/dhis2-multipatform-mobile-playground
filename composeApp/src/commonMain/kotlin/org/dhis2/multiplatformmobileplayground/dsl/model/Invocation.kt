package org.dhis2.multiplatformmobileplayground.dsl.model

data class Invocation(
    val commandName: String,
    val args: List<String>
)
