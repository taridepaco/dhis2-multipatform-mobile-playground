package org.dhis2.multiplatformmobileplayground.data.repository

import org.dhis2.multiplatformmobileplayground.dsl.catalog.CommandRegistry
import org.dhis2.multiplatformmobileplayground.dsl.llm.InterpretResult
import org.dhis2.multiplatformmobileplayground.dsl.llm.NaturalLanguageInterpreter

actual object NaturalLanguageInterpreterFactory {
    actual fun create(registry: CommandRegistry): NaturalLanguageInterpreter = UnavailableInterpreter

    private object UnavailableInterpreter : NaturalLanguageInterpreter {
        override val isAvailable: Boolean = false
        override suspend fun warmUp() = Unit
        override suspend fun interpret(text: String): InterpretResult =
            InterpretResult.Failure("Natural language interpretation not available on desktop")
    }
}
