package org.dhis2.multiplatformmobileplayground.dsl.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import org.dhis2.multiplatformmobileplayground.BuildConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.dhis2.multiplatformmobileplayground.dsl.catalog.CommandRegistry
import java.io.File

/**
 * On-device natural-language interpreter backed by the MediaPipe LLM Inference API (Google AI Edge),
 * the same engine used by the AI Edge Gallery app. It loads a Gemma model file and runs entirely
 * on-device, so it works on any sufficiently capable device (no AICore required).
 *
 * The model (~2.5 GB) is not bundled with the app. On first [warmUp] it is downloaded into app-private
 * storage; subsequent runs reuse the cached file. The model on Hugging Face is gated, so [authToken]
 * (a Hugging Face access token for an account that accepted the Gemma license) must be supplied for
 * the download to succeed — otherwise warm-up fails gracefully and the app falls back to the DSL.
 *
 * The API is text-in/text-out (no function-calling), so command selection is done by prompting the
 * model with the command catalog and asking it to reply in a small, strict format that we parse and
 * route through [ToolCallMapper].
 */
class Gemma4NaturalLanguageInterpreter(
    private val context: Context,
    private val registry: CommandRegistry,
    private val toolCallMapper: ToolCallMapper = ToolCallMapper(registry),
    private val modelUrl: String = DEFAULT_MODEL_URL,
    private val authToken: String? = DEFAULT_AUTH_TOKEN,
    modelFile: File = File(context.filesDir, "llm/$MODEL_FILE_NAME")
) : NaturalLanguageInterpreter {

    private val modelPath: String = modelFile.absolutePath
    private val downloader = ModelDownloader()

    // True means "this platform can run the LLM"; the model is fetched/loaded lazily in warmUp().
    // Set false if download or model load fails, which routes the resolver to the DSL fallback.
    @Volatile
    private var available = true

    @Volatile
    private var steadyState = false

    private val mutex = Mutex()
    private var engine: LlmInference? = null

    override val isAvailable: Boolean
        get() = available

    override suspend fun warmUp(onProgress: (InterpreterState) -> Unit) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val modelReady = ensureModelDownloaded(onProgress) && loadModel()
                available = modelReady
                steadyState = true
            }
        }
    }

    /** Downloads the model if it isn't present yet. Returns false on any download/storage error. */
    private suspend fun ensureModelDownloaded(onProgress: (InterpreterState) -> Unit): Boolean {
        val file = File(modelPath)
        if (file.exists() && file.length() > 0L) return true
        return runCatching {
            downloader.download(modelUrl, file, authToken) { progress ->
                onProgress(InterpreterState.DownloadingModel(progress))
            }
        }.isSuccess
    }

    /** Loads the model into the inference engine. Catches OOM / native init errors and returns false. */
    private fun loadModel(): Boolean = runCatching {
        engine = LlmInference.createFromOptions(
            context,
            LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_TOKENS)
                .setMaxTopK(MAX_TOP_K)
                .build()
        )
    }.onFailure { Log.e(TAG, "Model load failed", it) }.isSuccess

    override suspend fun interpret(text: String): InterpretResult {
        val engine = engine
        if (!available || engine == null) {
            return InterpretResult.Failure("Gemma 4 model not available on this device")
        }
        val timeout = if (steadyState) STEADY_TIMEOUT_MS else COLD_TIMEOUT_MS
        return try {
            withTimeout(timeout) {
                mutex.withLock { interpretInternal(engine, text) }
            }
        } catch (e: TimeoutCancellationException) {
            InterpretResult.Failure("Interpretation timed out. Try a shorter request.")
        } catch (e: Throwable) {
            Log.e(TAG, "Interpretation failed", e)
            InterpretResult.Failure("Interpretation error: ${e.message}")
        }
    }

    private suspend fun interpretInternal(engine: LlmInference, text: String): InterpretResult {
        val output = withContext(Dispatchers.IO) {
            LlmInferenceSession.createFromOptions(
                engine,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTemperature(0.0f)
                    .setTopK(1)
                    .build()
            ).use { session ->
                session.addQueryChunk(buildPrompt(text))
                session.generateResponse()
            }
        }.trim()
        Log.d(TAG, "Model output: '$output'")
        if (output.isBlank()) return InterpretResult.Failure("Empty response from model")
        return parseResponse(output)
    }

    private fun buildPrompt(text: String): String = buildString {
        appendLine(SYSTEM_INSTRUCTION)
        appendLine()
        appendLine("Available commands:")
        appendLine(buildCatalog())
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

    /** Compact, low-token command listing: `name(req1, req2, [opt]): description`. */
    private fun buildCatalog(): String = registry.allSpecs().joinToString("\n") { spec ->
        val params = spec.parameters.joinToString(", ") { p ->
            if (p.required) p.name else "[${p.name}]"
        }
        "- ${spec.name}($params): ${spec.description}"
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

    companion object {
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$MODEL_FILE_NAME"

        // The Gemma model is gated on Hugging Face; the token comes from BuildConfig.HF_TOKEN,
        // which is populated from local.properties (HF_TOKEN) or the HF_TOKEN env var at build time.
        // Null when unset, in which case the download fails and the app falls back to the DSL.
        val DEFAULT_AUTH_TOKEN: String? = BuildConfig.HF_TOKEN.ifBlank { null }

        // The first generation after model load includes graph warm-up, so allow extra time.
        private const val TAG = "Gemma4Interpreter"
        private const val COLD_TIMEOUT_MS = 60_000L
        private const val STEADY_TIMEOUT_MS = 20_000L
        // We only need a short `CALL`/`CLARIFY` reply; a small budget bounds worst-case latency.
        private const val MAX_TOKENS = 512
        private const val MAX_TOP_K = 64
        private const val SYSTEM_INSTRUCTION =
            "You are a DHIS2 notebook assistant. Map the user's natural-language request to exactly " +
            "one of the registered commands. If you cannot confidently map the request, ask for " +
            "clarification instead of guessing."
    }
}
