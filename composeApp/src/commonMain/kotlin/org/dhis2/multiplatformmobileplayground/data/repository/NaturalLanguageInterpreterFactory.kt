package org.dhis2.multiplatformmobileplayground.data.repository

import org.dhis2.multiplatformmobileplayground.dsl.catalog.CommandRegistry
import org.dhis2.multiplatformmobileplayground.dsl.llm.NaturalLanguageInterpreter

expect object NaturalLanguageInterpreterFactory {
    fun create(registry: CommandRegistry): NaturalLanguageInterpreter
}
