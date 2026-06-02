package org.dhis2.multiplatformmobileplayground.data.repository

import android.content.Context
import org.dhis2.multiplatformmobileplayground.dsl.catalog.CommandRegistry
import org.dhis2.multiplatformmobileplayground.dsl.llm.Gemma4NaturalLanguageInterpreter
import org.dhis2.multiplatformmobileplayground.dsl.llm.NaturalLanguageInterpreter

actual object NaturalLanguageInterpreterFactory {
    private lateinit var applicationContext: Context

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    actual fun create(registry: CommandRegistry): NaturalLanguageInterpreter =
        Gemma4NaturalLanguageInterpreter(registry = registry)
}
