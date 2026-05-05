package org.dhis2.multiplatformmobileplayground.dsl.model

data class CommandSpec(
    val name: String,
    val description: String,
    val parameters: List<ParamSpec>,
    val examples: List<String>,
    val readOnly: Boolean,
    val returns: String
)
