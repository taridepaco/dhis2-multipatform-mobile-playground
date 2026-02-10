package org.dhis2.multiplatformmobileplayground.model

data class Program(
    val id: String,
    val name: String,
    val displayName: String,
    val description: String? = null
)