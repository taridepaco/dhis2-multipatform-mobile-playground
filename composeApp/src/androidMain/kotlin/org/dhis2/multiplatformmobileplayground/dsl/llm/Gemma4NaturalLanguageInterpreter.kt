package org.dhis2.multiplatformmobileplayground.dsl.llm

import android.content.Context
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.GenerativeModelOptions
import com.google.mlkit.genai.prompt.PromptClient
import com.google.mlkit.genai.prompt.content.Content
import com.google.mlkit.genai.prompt.content.FunctionCallPart
import com.google.mlkit.genai.prompt.content.FunctionDeclaration
import com.google.mlkit.genai.prompt.content.Schema
import com.google.mlkit.genai.prompt.content.TextPart
import com.google.mlkit.genai.prompt.content.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.dhis2.multiplatformmobileplayground.dsl.catalog.CommandRegistry

class Gemma4NaturalLanguageInterpreter(
    private val context: Context,
    private val registry: CommandRegistry,
    private val toolCallMapper: ToolCallMapper = ToolCallMapper(registry)
) : NaturalLanguageInterpreter {

    override val isAvailable: Boolean = checkAvailability()

    private val mutex = Mutex()
    private var model: GenerativeModel? = null
    private var steadyState = false

    override suspend fun warmUp() {
        if (!isAvailable) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                model = buildModel()
                runCatching {
                    withTimeout(COLD_TIMEOUT_MS) {
                        model?.generateContent(Content.fromText("ping"))
                    }
                }
                steadyState = true
            }
        }
    }

    override suspend fun interpret(text: String): InterpretResult {
        if (!isAvailable || model == null) {
            return InterpretResult.Failure("Gemma 4 not available on this device")
        }
        val timeout = if (steadyState) STEADY_TIMEOUT_MS else COLD_TIMEOUT_MS
        return try {
            withTimeout(timeout) {
                mutex.withLock { interpretInternal(text) }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            InterpretResult.Failure("Interpretation timed out. Try a shorter request.")
        } catch (e: Exception) {
            InterpretResult.Failure("Interpretation error: ${e.message}")
        }
    }

    private suspend fun interpretInternal(text: String): InterpretResult {
        val response = withContext(Dispatchers.IO) {
            model?.generateContent(Content.fromText(text))
        } ?: return InterpretResult.Failure("No response from model")

        val functionCall = response.candidates
            .firstOrNull()
            ?.content
            ?.parts
            ?.filterIsInstance<FunctionCallPart>()
            ?.firstOrNull()

        if (functionCall != null) {
            val args = functionCall.args.mapValues { it.value.toString() }
            return toolCallMapper.map(functionCall.name, args)
        }

        val textContent = response.candidates
            .firstOrNull()
            ?.content
            ?.parts
            ?.filterIsInstance<TextPart>()
            ?.joinToString("") { it.text }
            ?.takeIf { it.isNotBlank() }

        return if (textContent != null) {
            InterpretResult.Clarification(textContent)
        } else {
            InterpretResult.Failure("Empty response from model")
        }
    }

    private fun checkAvailability(): Boolean = try {
        PromptClient.isAvailable(context)
    } catch (e: Exception) {
        false
    }

    private fun buildModel(): GenerativeModel {
        val functionDeclarations = registry.allSpecs().map { spec ->
            val properties = spec.parameters.associate { param ->
                param.name to Schema.string(param.description)
            }
            val required = spec.parameters.filter { it.required }.map { it.name }
            FunctionDeclaration(
                name = spec.name,
                description = spec.description,
                parameters = Schema.obj(properties = properties, required = required)
            )
        }
        return GenerativeModel(
            context = context,
            options = GenerativeModelOptions(
                systemInstruction = SYSTEM_INSTRUCTION,
                tools = listOf(Tool(functionDeclarations = functionDeclarations))
            )
        )
    }

    companion object {
        private const val COLD_TIMEOUT_MS = 15_000L
        private const val STEADY_TIMEOUT_MS = 8_000L
        private const val SYSTEM_INSTRUCTION =
            "You are a DHIS2 notebook assistant. Map the user's natural-language request to exactly " +
            "one of the registered tool functions. If you cannot confidently map the request, " +
            "respond with a plain-text clarification message — do not call any tool."
    }
}
