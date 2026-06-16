package org.dhis2.multiplatformmobileplayground.data.repository

import org.dhis2.multiplatformmobileplayground.dsl.executor.DslExecutor
import org.dhis2.multiplatformmobileplayground.dsl.llm.DslInputResolver
import org.dhis2.multiplatformmobileplayground.dsl.llm.InputResolver

actual object InputResolverFactory {
    actual fun create(executor: DslExecutor): InputResolver = DslInputResolver()
}
