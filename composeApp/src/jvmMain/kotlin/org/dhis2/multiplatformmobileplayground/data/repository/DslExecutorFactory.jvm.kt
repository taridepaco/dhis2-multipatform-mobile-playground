package org.dhis2.multiplatformmobileplayground.data.repository

import org.dhis2.multiplatformmobileplayground.dsl.catalog.CommandRegistry
import org.dhis2.multiplatformmobileplayground.dsl.catalog.CommandsCatalogCommand
import org.dhis2.multiplatformmobileplayground.dsl.catalog.DescribeCommand
import org.dhis2.multiplatformmobileplayground.dsl.catalog.HelpCommand
import org.dhis2.multiplatformmobileplayground.dsl.executor.DslExecutor
import org.dhis2.multiplatformmobileplayground.dsl.executor.RegistryDslExecutor

actual object DslExecutorFactory {
    actual fun create(): DslExecutor {
        val registry = CommandRegistry()
        registry.register(HelpCommand(registry))
        registry.register(DescribeCommand(registry))
        registry.register(CommandsCatalogCommand(registry))
        return RegistryDslExecutor(registry)
    }
}
