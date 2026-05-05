package org.dhis2.multiplatformmobileplayground.dsl.executor

import org.dhis2.multiplatformmobileplayground.dsl.model.CommandSpec
import org.dhis2.multiplatformmobileplayground.dsl.model.DslResult

interface CommandHandler {
    val spec: CommandSpec
    suspend fun execute(args: List<String>): DslResult
}
