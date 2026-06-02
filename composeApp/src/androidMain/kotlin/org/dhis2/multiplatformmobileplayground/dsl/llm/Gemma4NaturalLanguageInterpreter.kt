package org.dhis2.multiplatformmobileplayground.dsl.llm

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.dhis2.multiplatformmobileplayground.dsl.catalog.CommandRegistry

/**
 * On-device natural-language interpreter backed by ML Kit's GenAI Prompt API (Gemini Nano).
 *
 * The Prompt API is text-in/text-out and has no function-calling support, so command selection is
 * done by prompting the model with the command catalog and asking it to reply in a small, strict
 * format that we parse and route through [ToolCallMapper].
 */
class Gemma4NaturalLanguageInterpreter(
    private val registry: CommandRegistry,
    private val toolCallMapper: ToolCallMapper = ToolCallMapper(registry)
) : NaturalLanguageInterpreter {

    // Whether the device can run the model can only be determined via a suspend status check, so we
    // assume availability at construction (routing to this interpreter) and refine it in warmUp().
    @Volatile
    private var available = true

    @Volatile
    private var steadyState = false

    private val mutex = Mutex()
    private var model: GenerativeModel? = null

    override val isAvailable: Boolean
        get() = available

    override suspend fun warmUp() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val client = Generation.getClient().also { model = it }
                    if (ensureModelReady(client)) {
                        available = true
                        withTimeout(COLD_TIMEOUT_MS) { client.warmup() }
                    } else {
                        available = false
                    }
                }.onFailure { available = false }
                steadyState = true
            }
        }
    }

    override suspend fun interpret(text: String): InterpretResult {
        val client = model
        if (!available || client == null) {
            return InterpretResult.Failure("Gemma 4 not available on this device")
        }
        val timeout = if (steadyState) STEADY_TIMEOUT_MS else COLD_TIMEOUT_MS
        return try {
            withTimeout(timeout) {
                mutex.withLock { interpretInternal(client, text) }
            }
        } catch (e: TimeoutCancellationException) {
            InterpretResult.Failure("Interpretation timed out. Try a shorter request.")
        } catch (e: Exception) {
            InterpretResult.Failure("Interpretation error: ${e.message}")
        }
    }

    private suspend fun interpretInternal(client: GenerativeModel, text: String): InterpretResult {
        val response = withContext(Dispatchers.IO) {
            client.generateContent(
                generateContentRequest(TextPart(buildPrompt(text))) {
                    temperature = 0.0f
                    candidateCount = 1
                }
            )
        }
        val output = response.candidates.firstOrNull()?.text?.trim().orEmpty()
        if (output.isBlank()) return InterpretResult.Failure("Empty response from model")
        return parseResponse(output)
    }

    private fun buildPrompt(text: String): String = buildString {
        appendLine(SYSTEM_INSTRUCTION)
        appendLine()
        appendLine("Available commands (JSON catalog):")
        appendLine(registry.toJsonSchema())
        appendLine()
        appendLine("Response format — reply with ONLY one of the following, and nothing else:")
        appendLine("1. To invoke a command, a line `CALL <command_name>` followed by one")
        appendLine("   `<param_name>: <value>` line per argument (omit optional params you don't need).")
        appendLine("2. To ask for clarification, a single line `CLARIFY: <your message>`.")
        appendLine("Use only command and parameter names that appear in the catalog above.")
        appendLine()
        appendLine("User request:")
        append(text)
    }

    private fun parseResponse(raw: String): InterpretResult {
        val lines = raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("```") }
        val first = lines.firstOrNull()
            ?: return InterpretResult.Failure("Empty response from model")

        if (first.startsWith("CLARIFY", ignoreCase = true)) {
            val message = first.substringAfter(':', "").trim()
            return InterpretResult.Clarification(message.ifBlank { raw.trim() })
        }

        if (first.startsWith("CALL", ignoreCase = true)) {
            val command = first.removePrefix(first.take(4))
                .trimStart(':', ' ')
                .trim()
            if (command.isEmpty()) {
                return InterpretResult.Failure("Model did not specify a command")
            }
            val args = lines.drop(1).mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) return@mapNotNull null
                line.substring(0, separator).trim() to line.substring(separator + 1).trim()
            }.toMap()
            return toolCallMapper.map(command, args)
        }

        // The model replied with free-form text instead of following the format — surface it.
        return InterpretResult.Clarification(raw.trim())
    }

    private suspend fun ensureModelReady(client: GenerativeModel): Boolean =
        when (client.checkStatus()) {
            FeatureStatus.AVAILABLE -> true
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                client.download().collect { /* drain until the download completes */ }
                client.checkStatus() == FeatureStatus.AVAILABLE
            }
            else -> false
        }

    companion object {
        private const val COLD_TIMEOUT_MS = 15_000L
        private const val STEADY_TIMEOUT_MS = 8_000L
        private const val SYSTEM_INSTRUCTION =
            "You are a DHIS2 notebook assistant. Map the user's natural-language request to exactly " +
            "one of the registered commands. If you cannot confidently map the request, ask for " +
            "clarification instead of guessing."
    }
}
