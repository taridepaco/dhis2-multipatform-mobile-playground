package org.dhis2.multiplatformmobileplayground.dsl.llm

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import org.dhis2.multiplatformmobileplayground.BuildConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.dhis2.multiplatformmobileplayground.dsl.catalog.CommandRegistry
import java.io.File
import java.util.concurrent.Executor

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
    modelFile: File = File(context.filesDir, "llm/$MODEL_FILE_NAME"),
    // App-specific external storage is `adb push`-able and lets the (slow) download be reused: the
    // model is exported here once downloaded and imported back before re-downloading. Null when no
    // external storage is mounted. Note this dir is still wiped on uninstall, but re-seeding it is a
    // fast local copy/push rather than a multi-GB network download.
    seedFile: File? = context.getExternalFilesDir(null)?.let { File(it, "llm/$MODEL_FILE_NAME") }
) : NaturalLanguageInterpreter {

    private val modelPath: String = modelFile.absolutePath
    private val seedPath: String? = seedFile?.absolutePath
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
                Log.d(TAG, "Warm-up start. modelPath=$modelPath, seedPath=$seedPath, hasAuthToken=${!authToken.isNullOrBlank()}")
                val present = ensureModelPresent(onProgress)
                val modelReady = present && loadModel()
                available = modelReady
                steadyState = true
                // Preserve the model in external storage so a reinstall reuses it instead of
                // downloading ~2.5 GB again. Best-effort: never blocks readiness from succeeding.
                if (modelReady) exportSeedIfMissing()
                Log.d(
                    TAG,
                    "Warm-up done. present=$present, available=$available" +
                        (if (!available) " -> falling back to DSL" else "")
                )
            }
        }
    }

    /**
     * Ensures the model exists in app storage, in priority order: already present → import a
     * preserved copy from external storage → download. Returns false on any failure.
     */
    private suspend fun ensureModelPresent(onProgress: (InterpreterState) -> Unit): Boolean {
        val file = File(modelPath)
        if (file.exists() && file.length() > 0L) {
            Log.d(TAG, "Model already present (${file.length()} bytes), skipping download")
            return true
        }
        if (importFromSeed(file)) return true
        return runCatching {
            Log.d(TAG, "Downloading model from $modelUrl")
            downloader.download(modelUrl, file, authToken) { progress ->
                onProgress(InterpreterState.DownloadingModel(progress))
            }
            Log.d(TAG, "Model download complete (${file.length()} bytes)")
        }.onFailure { Log.e(TAG, "Model download failed", it) }.isSuccess
    }

    /** Copies a previously preserved model from external storage into app storage, skipping the download. */
    private fun importFromSeed(modelFile: File): Boolean {
        val seed = seedPath?.let { File(it) } ?: return false
        if (!seed.exists() || seed.length() <= 0L) return false
        return runCatching {
            Log.d(TAG, "Importing model from seed $seedPath (${seed.length()} bytes)")
            modelFile.parentFile?.mkdirs()
            seed.copyTo(modelFile, overwrite = true, bufferSize = COPY_BUFFER_BYTES)
            Log.d(TAG, "Model import complete (${modelFile.length()} bytes)")
        }.onFailure {
            Log.e(TAG, "Model import from seed failed", it)
            modelFile.delete() // don't leave a half-copied file behind
        }.isSuccess
    }

    /** Exports the downloaded model to external storage so it survives a reinstall. Best-effort, non-fatal. */
    private fun exportSeedIfMissing() {
        val seed = seedPath?.let { File(it) } ?: return
        val model = File(modelPath)
        if (!model.exists() || model.length() <= 0L) return
        if (seed.exists() && seed.length() == model.length()) return // already preserved

        // Skip if the seed location can't fit the model. On an emulator external storage shares the
        // (often full) data partition, so attempting the copy would only fail with ENOSPC and leave
        // a truncated file behind.
        seed.parentFile?.mkdirs()
        val free = (seed.parentFile ?: seed).usableSpace
        if (free < model.length() + SEED_STORAGE_HEADROOM_BYTES) {
            Log.d(TAG, "Skipping model export: only $free bytes free for a ${model.length()} byte model")
            return
        }
        runCatching {
            Log.d(TAG, "Exporting model to seed $seedPath for reuse across reinstalls")
            model.copyTo(seed, overwrite = true, bufferSize = COPY_BUFFER_BYTES)
            Log.d(TAG, "Model export complete (${seed.length()} bytes)")
        }.onFailure {
            Log.e(TAG, "Model export to seed failed (non-fatal)", it)
            seed.delete() // don't leave a truncated seed that could be wrongly imported later
        }
    }

    /**
     * Loads the model into the inference engine. Prefers the GPU backend (much faster) when the
     * device supports it, falling back to CPU otherwise. GPU support is detected up-front rather
     * than via try/catch because MediaPipe's GPU backend hard-crashes (SIGSEGV) when OpenCL is
     * missing — e.g. on emulators — instead of throwing a catchable exception. Returns false if no
     * backend works.
     */
    private fun loadModel(): Boolean {
        if (engine != null) return true // already loaded; don't recreate the engine on re-warm-up
        val backends = if (isGpuBackendSupported()) {
            listOf(LlmInference.Backend.GPU, LlmInference.Backend.CPU)
        } else {
            Log.d(TAG, "GPU backend not supported on this device; using CPU")
            listOf(LlmInference.Backend.CPU)
        }
        for (backend in backends) {
            val loaded = runCatching {
                Log.d(TAG, "Loading model with $backend backend…")
                engine = LlmInference.createFromOptions(
                    context,
                    LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelPath)
                        .setMaxTokens(MAX_TOKENS)
                        .setMaxTopK(MAX_TOP_K)
                        .setPreferredBackend(backend)
                        .build()
                )
            }.onFailure { Log.e(TAG, "Model load with $backend backend failed", it) }.isSuccess
            if (loaded) {
                Log.d(TAG, "Model loaded with $backend backend")
                return true
            }
        }
        return false
    }

    /**
     * Whether to attempt the GPU backend. Emulators have no OpenCL and forcing GPU there crashes the
     * process natively (uncatchable SIGSEGV), so we exclude them — that's the one reliably-detectable
     * unsupported case. Physical devices attempt GPU; the manifest's <uses-native-library> entries
     * let LiteRT dlopen the vendor OpenCL driver, and if a real device still lacks GPU support the
     * loadModel() loop falls back to CPU. (Probing exact OpenCL driver paths proved too unreliable
     * across OEMs — it false-negatived real devices and kept them on slow CPU.)
     */
    private fun isGpuBackendSupported(): Boolean = !isProbablyEmulator()

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
        // We use the async (streaming) generation API so the await is cancellable: when withTimeout
        // fires we cancel the underlying future to abort generation, instead of being stuck behind a
        // blocking native call.
        return try {
            withTimeout(timeout) {
                mutex.withLock { interpretInternal(engine, text) }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "interpret() timed out after ${timeout}ms (native generation may still be running)")
            InterpretResult.Failure("Interpretation timed out. Try a shorter request.")
        } catch (e: Throwable) {
            Log.e(TAG, "Interpretation failed", e)
            InterpretResult.Failure("Interpretation error: ${e.message}")
        }
    }

    private suspend fun interpretInternal(engine: LlmInference, text: String): InterpretResult {
        val output = withContext(Dispatchers.IO) {
            val session = LlmInferenceSession.createFromOptions(
                engine,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTemperature(0.0f)
                    .setTopK(1)
                    .build()
            )
            val prompt = buildPrompt(text)
            Log.d(TAG, "Submitting prompt (${prompt.length} chars) and generating response…")
            Log.d(TAG, "Full prompt sent to model:\n$prompt")
            session.addQueryChunk(prompt)

            val startedAt = SystemClock.elapsedRealtime()
            val result = CompletableDeferred<String>()
            val builder = StringBuilder()
            // Streams partial tokens: logging each one tells us whether the model is producing output
            // slowly or is genuinely stuck (no tokens at all).
            val future = session.generateResponseAsync { partial, done ->
                if (!partial.isNullOrEmpty()) builder.append(partial)
                Log.d(
                    TAG,
                    "partial token (+${partial?.length ?: 0} chars, total=${builder.length}, " +
                        "${SystemClock.elapsedRealtime() - startedAt}ms), done=$done"
                )
                if (done) result.complete(builder.toString())
            }
            // Surface a generation failure (otherwise the listener might never report done).
            future.addListener(
                { runCatching { future.get() }.onFailure { result.completeExceptionally(it) } },
                Executor { it.run() }
            )
            try {
                result.await()
            } finally {
                // On timeout/cancellation, ask the engine to abort: cancelGenerateResponseAsync()
                // releases the engine lock (future.cancel() does not), so the next request isn't
                // rejected with "Previous invocation still processing".
                if (!result.isCompleted) {
                    Log.w(TAG, "Cancelling in-flight generation after ${SystemClock.elapsedRealtime() - startedAt}ms")
                    runCatching { session.cancelGenerateResponseAsync() }
                }
                runCatching { session.close() }
                Log.d(TAG, "Generation finished in ${SystemClock.elapsedRealtime() - startedAt}ms")
            }
        }
        val sanitized = sanitize(output)
        Log.d(TAG, "Model output (raw ${output.length} chars, sanitized ${sanitized.length}): '$sanitized'")
        if (sanitized.isBlank()) return InterpretResult.Failure("Empty response from model")
        return parseResponse(sanitized)
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

    private fun buildPrompt(text: String): String {
        val instructions = buildString {
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
        // Gemma instruction-tuned chat template. Without the turn markers the model doesn't enter
        // "answer" mode and rambles past its reply, emitting <end_of_turn>/<eos> as literal text and
        // running until MAX_TOKENS (slow + noisy). The trailing `model` turn primes the response.
        return "<start_of_turn>user\n$instructions<end_of_turn>\n<start_of_turn>model\n"
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

        // The model may emit preamble before the directive (and sometimes repeats it), so scan for
        // the first CALL/CLARIFY line instead of assuming it's line one.
        val callIdx = lines.indexOfFirst { it.startsWith("CALL", ignoreCase = true) }
        val clarifyIdx = lines.indexOfFirst { it.startsWith("CLARIFY", ignoreCase = true) }

        if (callIdx >= 0 && (clarifyIdx < 0 || callIdx <= clarifyIdx)) {
            val afterCall = lines[callIdx].substring(CALL_PREFIX_LENGTH).trimStart(':', ' ').trim()
            // Command name is the first token. The model often mimics the catalog's function syntax,
            // e.g. `CALL d2.programs.list(limit: 10)`, so stop at whitespace, ':' or '(' (which would
            // otherwise leave parentheses on the name and fail the lookup).
            val command = afterCall.takeWhile { !it.isWhitespace() && it != ':' && it != '(' }
            if (command.isEmpty()) {
                return InterpretResult.Failure("Model did not specify a command")
            }
            // Args may appear inline (inside parens or after a colon) and/or one per following line.
            val inline = afterCall.removePrefix(command).trim()
                .let { if (it.startsWith("(") && it.endsWith(")")) it.substring(1, it.length - 1) else it }
                .trimStart(':', ' ')
            val argFragments = inline.split(',', ';', '\n') +
                lines.drop(callIdx + 1)
                    .takeWhile { !it.startsWith("CALL", true) && !it.startsWith("CLARIFY", true) }
            val args = argFragments.mapNotNull { fragment ->
                val frag = fragment.trim().trim('(', ')').trim()
                val separator = frag.indexOfFirst { it == ':' || it == '=' }
                if (separator <= 0) return@mapNotNull null
                frag.substring(0, separator).trim() to
                    frag.substring(separator + 1).trim().trim(',', ')', '"', '\'', ' ')
            }.toMap()
            return toolCallMapper.map(command, args)
        }

        if (clarifyIdx >= 0) {
            val message = lines[clarifyIdx].substringAfter(':', "").trim()
            return InterpretResult.Clarification(message.ifBlank { raw.trim() })
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

        // On-device generation is slow, especially the first run (graph warm-up) and on CPU-only
        // hardware / emulators where prompt prefill alone can take a minute or more. Keep these
        // generous so a real inference completes instead of being cancelled mid-flight; on a device
        // with GPU/NNAPI acceleration it finishes well within these bounds.
        private const val TAG = "Gemma4Interpreter"
        private const val COLD_TIMEOUT_MS = 180_000L
        private const val STEADY_TIMEOUT_MS = 120_000L
        // We only need a short `CALL`/`CLARIFY` reply; a small budget bounds worst-case latency.
        private const val MAX_TOKENS = 512
        private const val MAX_TOP_K = 64

        private const val CALL_PREFIX_LENGTH = 4 // "CALL"
        // The reply ends at the first of these; anything after is the model failing to stop.
        private val STOP_MARKERS = listOf("<end_of_turn>", "<eos>", "<turn>")
        // Residual special tokens to strip from the kept text.
        private val SPECIAL_TOKEN =
            Regex("<(?:start_of_turn|end_of_turn|eos|bos|pad|turn|sep|unk)>")
        // Larger buffer than the default 8 KB to keep the multi-GB model import/export reasonably fast.
        private const val COPY_BUFFER_BYTES = 1 shl 20 // 1 MB
        // Free space required beyond the model size before exporting a seed copy.
        private const val SEED_STORAGE_HEADROOM_BYTES = 256L * 1024L * 1024L // 256 MB
        private const val SYSTEM_INSTRUCTION =
            "You are a DHIS2 notebook assistant. Map the user's natural-language request to exactly " +
            "one of the registered commands. If you cannot confidently map the request, ask for " +
            "clarification instead of guessing."
    }
}
