package org.dhis2.multiplatformmobileplayground.data.repository

import org.dhis2.multiplatformmobileplayground.dsl.executor.DslExecutor
import org.dhis2.multiplatformmobileplayground.dsl.llm.InputResolver
import org.dhis2.multiplatformmobileplayground.dsl.llm.LlmInputResolver

actual object InputResolverFactory {
    actual fun create(executor: DslExecutor): InputResolver =
        LlmInputResolver(NaturalLanguageInterpreterFactory.create(executor.registry))
}
