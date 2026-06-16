package org.dhis2.multiplatformmobileplayground.dsl.llm

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.dhis2.multiplatformmobileplayground.BuildConfig
import org.dhis2.multiplatformmobileplayground.dsl.catalog.CommandRegistry
import java.io.File

/**
 * On-device natural-language interpreter backed by the LiteRT-LM runtime (Google AI Edge), the same
 * runtime the AI Edge Gallery uses to run `.litertlm` models. It loads a Gemma model file and runs
 * entirely on-device, preferring GPU acceleration where available. (We previously used MediaPipe
 * `tasks-genai`, but its GPU accelerator failed to load for this model on real hardware, pinning us
 * to slow CPU inference; LiteRT-LM is the runtime the `.litertlm` format is built for.)
 *
 * The model (~2.5 GB) is not bundled with the app. On first [warmUp] it is downloaded into app-private
 * storage; subsequent runs reuse the cached file. The model on Hugging Face is gated, so [authToken]
 * (a Hugging Face access token for an account that accepted the Gemma license) must be supplied for
 * the download to succeed — otherwise warm-up fails gracefully and the app falls back to the DSL.
 *
 * Inference is text-in/text-out (no function-calling), so command selection is done by giving the
 * model the command catalog as a system instruction and asking it to reply in a small, strict format
 * that we parse and route through [ToolCallMapper].
 */
class Gemma4NaturalLanguageInterpreter(
    private val context: Context,
    private val registry: CommandRegistry,
    private val toolCallMapper: ToolCallMapper = ToolCallMapper(registry),
    private val modelUrl: String = DEFAULT_MODEL_URL,
    private val authToken: String? = DEFAULT_AUTH_TOKEN,
    // Stored in app-specific external storage when available: that dir is `adb push`-able, so the
    // slow, gated multi-GB download can be re-seeded across clean installs with a fast local push
    // instead of a network fetch. The model is loaded directly from here — there is no second
    // internal copy. Falls back to internal filesDir when no external storage is mounted. (Both
    // locations are wiped on uninstall.)
    modelFile: File = defaultModelFile(context)
) : NaturalLanguageInterpreter {

    private val modelPath: String = modelFile.absolutePath
    private val cacheDir: String = context.cacheDir.absolutePath
    private val downloader = ModelDownloader()

    // True means "this platform can run the LLM"; the model is fetched/loaded lazily in warmUp().
    // Set false if download or model load fails, which routes the resolver to the DSL fallback.
    @Volatile
    private var available = true

    @Volatile
    private var steadyState = false

    private val mutex = Mutex()
    private var engine: Engine? = null

    override val isAvailable: Boolean
        get() = available

    override suspend fun warmUp(onProgress: (InterpreterState) -> Unit) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                Log.d(TAG, "Warm-up start. modelPath=$modelPath, hasAuthToken=${!authToken.isNullOrBlank()}")
                val present = ensureModelPresent(onProgress)
                val modelReady = present && loadModel()
                available = modelReady
                steadyState = true
                Log.d(
                    TAG,
                    "Warm-up done. present=$present, available=$available" +
                        (if (!available) " -> falling back to DSL" else "")
                )
            }
        }
    }

    /** Ensures the model exists in app storage: reuse it if already present, otherwise download it. */
    private suspend fun ensureModelPresent(onProgress: (InterpreterState) -> Unit): Boolean {
        val file = File(modelPath)
        if (file.exists() && file.length() > 0L) {
            Log.d(TAG, "Model already present (${file.length()} bytes), skipping download")
            return true
        }
        return runCatching {
            Log.d(TAG, "Downloading model from $modelUrl")
            downloader.download(modelUrl, file, authToken) { progress ->
                onProgress(InterpreterState.DownloadingModel(progress))
            }
            Log.d(TAG, "Model download complete (${file.length()} bytes)")
        }.onFailure { Log.e(TAG, "Model download failed", it) }.isSuccess
    }

    /**
     * Initializes the LiteRT-LM engine. Prefers the GPU backend (much faster) on physical devices,
     * falling back to CPU if GPU initialization fails. Emulators skip GPU entirely: they have no
     * OpenCL driver and forcing GPU there crashes the process natively (uncatchable SIGSEGV) rather
     * than throwing. Returns false if no backend initializes.
     */
    private fun loadModel(): Boolean {
        if (engine != null) return true // already initialized; don't reload on re-warm-up
        val backends = if (isProbablyEmulator()) {
            Log.d(TAG, "Emulator detected; using CPU backend only")
            listOf("CPU" to Backend.CPU())
        } else {
            listOf("GPU" to Backend.GPU(), "CPU" to Backend.CPU())
        }
        for ((label, backend) in backends) {
            val loaded = runCatching {
                Log.d(TAG, "Initializing LiteRT-LM engine ($label backend)…")
                val newEngine = Engine(
                    EngineConfig(modelPath = modelPath, backend = backend, cacheDir = cacheDir)
                )
                newEngine.initialize()
                engine = newEngine
            }.onFailure { Log.e(TAG, "Engine init ($label backend) failed", it) }.isSuccess
            if (loaded) {
                Log.d(TAG, "Engine initialized with $label backend")
                return true
            }
        }
        // No backend could initialize the model. Delete the file so the next warm-up
        // re-downloads a fresh copy; otherwise a present-but-unloadable model (corrupt or
        // incompletely seeded but non-zero length) would be reused forever and never re-fetched.
        Log.w(TAG, "All backends failed to load the model; deleting $modelPath for a fresh re-download")
        runCatching { File(modelPath).delete() }
        return false
    }

    private fun isProbablyEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.PRODUCT.contains("sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for")

    override suspend fun interpret(text: String): InterpretResult {
        val engine = engine
        if (!available || engine == null) {
            return InterpretResult.Failure("Gemma 4 model not available on this device")
        }
        val timeout = if (steadyState) STEADY_TIMEOUT_MS else COLD_TIMEOUT_MS
        Log.d(TAG, "interpret() start. steadyState=$steadyState, timeout=${timeout}ms, text='$text'")
        return try {
            // The streaming Flow is cancellable, so withTimeout actually aborts a slow generation.
            withTimeout(timeout) {
                mutex.withLock { interpretInternal(engine, text) }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "interpret() timed out after ${timeout}ms")
            InterpretResult.Failure("Interpretation timed out. Try a shorter request.")
        } catch (e: Throwable) {
            Log.e(TAG, "Interpretation failed", e)
            InterpretResult.Failure("Interpretation error: ${e.message}")
        }
    }

    private suspend fun interpretInternal(engine: Engine, text: String): InterpretResult {
        val systemInstruction = buildSystemInstruction()
        val output = withContext(Dispatchers.IO) {
            // A fresh conversation per request keeps inference stateless (no history bleed between
            // notebook commands). The conversation applies the model's chat template and stop tokens.
            val conversation = engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(systemInstruction),
                    samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0)
                )
            )
            try {
                Log.d(TAG, "System instruction (${systemInstruction.length} chars). User request: '$text'")
                val startedAt = SystemClock.elapsedRealtime()
                val builder = StringBuilder()
                conversation.sendMessageAsync(Contents.of(text)).collect { message ->
                    // Each streamed Message carries a partial chunk; concatenate the text parts.
                    val chunk = message.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                    if (chunk.isNotEmpty()) builder.append(chunk)
                    Log.d(
                        TAG,
                        "partial (+${chunk.length} chars, total=${builder.length}, " +
                            "${SystemClock.elapsedRealtime() - startedAt}ms)"
                    )
                }
                Log.d(TAG, "Generation finished in ${SystemClock.elapsedRealtime() - startedAt}ms")
                builder.toString()
            } finally {
                runCatching { conversation.close() }
            }
        }
        val sanitized = sanitize(output)
        Log.d(TAG, "Model output (raw ${output.length} chars, sanitized ${sanitized.length}): '$sanitized'")
        if (sanitized.isBlank()) return InterpretResult.Failure("Empty response from model")
        return parseResponse(sanitized, text)
    }

    /** Trims the reply at the first turn/sequence terminator and strips any residual special tokens. */
    private fun sanitize(raw: String): String {
        var text = raw
        for (stop in STOP_MARKERS) {
            val index = text.indexOf(stop)
            if (index >= 0) text = text.substring(0, index)
        }
        return text.replace(SPECIAL_TOKEN, "").trim()
    }

    /** System instruction = role + command catalog + the strict reply format. */
    private fun buildSystemInstruction(): String = buildString {
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
        append("Copy any IDs, UIDs or codes from the request EXACTLY — never change, shorten, or omit characters.")
    }

    /** Compact, low-token command listing: `name(req1, req2, [opt]): description`. */
    private fun buildCatalog(): String = registry.allSpecs().joinToString("\n") { spec ->
        val params = spec.parameters.joinToString(", ") { p ->
            if (p.required) p.name else "[${p.name}]"
        }
        "- ${spec.name}($params): ${spec.description}"
    }

    private fun parseResponse(raw: String, userText: String): InterpretResult {
        val lines = raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("```") }

        // The model may emit preamble before the directive (and sometimes repeats it), so scan for
        // the first CALL/CLARIFY line instead of assuming it's line one.
        val callIdx = lines.indexOfFirst { it.startsWith("CALL", ignoreCase = true) }
        val clarifyIdx = lines.indexOfFirst { it.startsWith("CLARIFY", ignoreCase = true) }

        if (callIdx >= 0 && (clarifyIdx < 0 || callIdx <= clarifyIdx)) {
            val afterCall = lines[callIdx].substring(CALL_PREFIX_LENGTH).trimStart(':', ' ').trim()
            return invocationFrom(afterCall, lines.drop(callIdx + 1), userText)
        }

        if (clarifyIdx >= 0) {
            val message = lines[clarifyIdx].substringAfter(':', "").trim()
            return InterpretResult.Clarification(message.ifBlank { raw.trim() })
        }

        // No CALL/CLARIFY directive. The model sometimes writes a bare command (e.g. `help()`),
        // dropping the CALL prefix. Accept it only when the first token is a registered command —
        // genuine free-form text won't match, so it still surfaces as a clarification.
        val firstLine = lines.firstOrNull()
        if (firstLine != null) {
            val candidate = extractCommandName(firstLine)
            if (candidate.isNotEmpty() && registry.find(candidate) != null) {
                return invocationFrom(firstLine, lines.drop(1), userText)
            }
        }

        // The model replied with free-form text instead of following the format — surface it.
        return InterpretResult.Clarification(raw.trim())
    }

    /**
     * Extracts a command name from the start of a line: skips leading non-identifier junk (the model
     * sometimes wraps names in `<...>`, backticks or `*`) and keeps only identifier characters, so
     * stray trailing markup like `d2.users.me>` or `help()` resolves to the bare command name.
     */
    private fun extractCommandName(line: String): String =
        line.dropWhile { !it.isLetterOrDigit() }
            .takeWhile { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }

    /**
     * Parses a command invocation from a `<command> [args]` line plus any following argument lines,
     * and maps it through [toolCallMapper]. The model often mimics the catalog's function syntax
     * (`d2.programs.list(limit: 10)`), so args are read from inside parens, after a colon, and/or one
     * per following line.
     */
    private fun invocationFrom(
        commandLine: String,
        followingLines: List<String>,
        userText: String
    ): InterpretResult {
        val command = extractCommandName(commandLine)
        if (command.isEmpty()) {
            return InterpretResult.Failure("Model did not specify a command")
        }
        val inline = commandLine.substringAfter(command, "").trim()
            .let { if (it.startsWith("(") && it.endsWith(")")) it.substring(1, it.length - 1) else it }
            .trimStart(':', ' ')
        val argFragments = inline.split(',', ';', '\n') +
            followingLines.takeWhile { !it.startsWith("CALL", true) && !it.startsWith("CLARIFY", true) }
        val args = argFragments.mapNotNull { fragment ->
            val frag = fragment.trim().trim('(', ')', '<', '>', '`').trim()
            val separator = frag.indexOfFirst { it == ':' || it == '=' }
            if (separator <= 0) return@mapNotNull null
            frag.substring(0, separator).trim() to
                frag.substring(separator + 1).trim().trim(',', ')', '"', '\'', '<', '>', '`', ' ')
        }.toMap()
        return toolCallMapper.map(command, correctUids(args, userText))
    }

    /**
     * LLMs don't reliably copy random identifiers, so a UID can come back mangled (e.g. a dropped
     * leading char). When an argument looks like a UID but doesn't match a valid DHIS2 UID, and the
     * user's original text contains a valid UID that overlaps it, prefer the verbatim UID the user
     * typed. Short/non-UID values (limits, codes) are left untouched.
     */
    private fun correctUids(args: Map<String, String>, userText: String): Map<String, String> {
        val inputUids = DHIS2_UID.findAll(userText).map { it.value }.toList()
        if (inputUids.isEmpty()) return args
        return args.mapValues { (_, value) ->
            if (!looksLikeUid(value) || DHIS2_UID.matches(value)) {
                value
            } else {
                inputUids.firstOrNull { uid ->
                    uid.contains(value, ignoreCase = true) || value.contains(uid, ignoreCase = true)
                }?.also { Log.d(TAG, "Corrected mangled UID '$value' -> '$it' from user input") } ?: value
            }
        }
    }

    private fun looksLikeUid(value: String): Boolean =
        value.length in 8..13 && value.all { it.isLetterOrDigit() }

    companion object {
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"

        /**
         * The model lives in app-specific external storage (`adb push`-able for re-seeding) when it is
         * mounted, falling back to internal storage otherwise. It is the single location the model is
         * downloaded into and loaded from — there is no separate internal copy.
         */
        private fun defaultModelFile(context: Context): File {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            return File(baseDir, "llm/$MODEL_FILE_NAME")
        }

        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$MODEL_FILE_NAME"

        // The Gemma model is gated on Hugging Face; the token comes from BuildConfig.HF_TOKEN,
        // which is populated from local.properties (HF_TOKEN) or the HF_TOKEN env var at build time.
        // Null when unset, in which case the download fails and the app falls back to the DSL.
        val DEFAULT_AUTH_TOKEN: String? = BuildConfig.HF_TOKEN.ifBlank { null }

        // On-device generation is slow, especially the first run (graph warm-up) and on CPU-only
        // hardware / emulators. Keep these generous so a real inference completes instead of being
        // cancelled mid-flight; with GPU acceleration it finishes well within these bounds.
        private const val TAG = "Gemma4Interpreter"
        private const val COLD_TIMEOUT_MS = 180_000L
        private const val STEADY_TIMEOUT_MS = 120_000L

        private const val CALL_PREFIX_LENGTH = 4 // "CALL"
        // The reply ends at the first of these; anything after is the model failing to stop.
        private val STOP_MARKERS = listOf("<end_of_turn>", "<eos>", "<turn>")
        // Residual special tokens to strip from the kept text.
        private val SPECIAL_TOKEN =
            Regex("<(?:start_of_turn|end_of_turn|eos|bos|pad|turn|sep|unk)>")
        // DHIS2 UID: exactly 11 chars, a leading letter then letters/digits.
        private val DHIS2_UID = Regex("\\b[A-Za-z][A-Za-z0-9]{10}\\b")
        private const val SYSTEM_INSTRUCTION =
            "You are a DHIS2 notebook assistant. Map the user's natural-language request to exactly " +
            "one of the registered commands. If you cannot confidently map the request, ask for " +
            "clarification instead of guessing."
    }
}
