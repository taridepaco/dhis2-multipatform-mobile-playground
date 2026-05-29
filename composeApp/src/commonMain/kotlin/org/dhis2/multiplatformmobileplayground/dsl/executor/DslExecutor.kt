package org.dhis2.multiplatformmobileplayground.dsl.executor

import org.dhis2.multiplatformmobileplayground.dsl.catalog.CommandRegistry
import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult
import org.dhis2.multiplatformmobileplayground.dsl.model.Invocation

interface DslExecutor {
    val registry: CommandRegistry
    suspend fun execute(invocation: Invocation): DslResult
}
