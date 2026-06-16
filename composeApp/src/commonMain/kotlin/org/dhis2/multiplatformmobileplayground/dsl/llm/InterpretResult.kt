package org.dhis2.multiplatformmobileplayground.dsl.llm

import org.dhis2.multiplatformmobileplayground.dsl.model.Invocation

sealed class InterpretResult {
    data class Resolved(
        val invocation: Invocation,
        val inferredCall: String?,
        val reasoning: String? = null
    ) : InterpretResult()

    data class Clarification(val message: String) : InterpretResult()

    data class Failure(val message: String) : InterpretResult()
}
