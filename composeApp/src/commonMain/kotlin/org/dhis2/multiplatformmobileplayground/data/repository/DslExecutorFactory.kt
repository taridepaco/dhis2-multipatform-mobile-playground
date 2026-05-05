package org.dhis2.multiplatformmobileplayground.data.repository

import org.dhis2.multiplatformmobileplayground.dsl.executor.DslExecutor

expect object DslExecutorFactory {
    fun create(): DslExecutor
}
