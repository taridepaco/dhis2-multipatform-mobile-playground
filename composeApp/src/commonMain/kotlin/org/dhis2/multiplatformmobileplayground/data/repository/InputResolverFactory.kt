package org.dhis2.multiplatformmobileplayground.data.repository

import org.dhis2.multiplatformmobileplayground.dsl.executor.DslExecutor
import org.dhis2.multiplatformmobileplayground.dsl.llm.InputResolver

expect object InputResolverFactory {
    fun create(executor: DslExecutor): InputResolver
}
