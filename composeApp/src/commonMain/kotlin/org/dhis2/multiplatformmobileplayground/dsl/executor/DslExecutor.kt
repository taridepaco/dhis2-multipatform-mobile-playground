package org.dhis2.multiplatformmobileplayground.dsl.executor

import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult
import org.dhis2.multiplatformmobileplayground.dsl.model.Invocation

interface DslExecutor {
    suspend fun execute(invocation: Invocation): DslResult
}
