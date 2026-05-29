package org.dhis2.multiplatformmobileplayground.data.repository

import org.dhis2.multiplatformmobileplayground.dsl.executor.DslExecutor
import org.dhis2.multiplatformmobileplayground.dsl.llm.DslInputResolver
import org.dhis2.multiplatformmobileplayground.dsl.llm.InputResolver
import org.dhis2.multiplatformmobileplayground.dsl.llm.LlmInputResolver

actual object InputResolverFactory {
    actual fun create(executor: DslExecutor): InputResolver {
        val interpreter = NaturalLanguageInterpreterFactory.create(executor.registry)
        return if (interpreter.isAvailable) {
            LlmInputResolver(interpreter = interpreter)
        } else {
            LlmInputResolver(
                interpreter = interpreter,
                fallback = DslInputResolver(),
                withDslFallback = true
            )
        }
    }
}
