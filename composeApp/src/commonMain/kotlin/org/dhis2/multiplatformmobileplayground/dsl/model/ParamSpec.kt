package org.dhis2.multiplatformmobileplayground.dsl.model

data class ParamSpec(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean
)
