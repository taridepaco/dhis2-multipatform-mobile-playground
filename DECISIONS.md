# Decisions

Append-only log of important decisions taken during the project. Newest at the bottom. Each entry should answer: **what** was decided, **why**, and **what it implies for future work**.

When the rationale of a decision changes, do not edit the original entry — add a new one that supersedes it and reference the old ID.

Format:

```
## NNNN — Short title (YYYY-MM-DD)

**Context.** What problem or constraint led to this.
**Decision.** What we chose.
**Consequences.** What this means going forward; what it rules out.
**Supersedes / Superseded by.** (optional) Cross-reference to other entry IDs.
```

---

## 0001 — Kotlin Multiplatform + Compose Multiplatform with single `composeApp` module (seed)

**Context.** Target Android, iOS, and Desktop (JVM) from one codebase, with a UI layer shared across all three.
**Decision.** Use KMP + Compose Multiplatform; keep all shared code in a single Gradle module (`composeApp/`).
**Consequences.** Source sets are organized by target (`commonMain`, `androidMain`, `iosMain`, `jvmMain`, `commonTest`, ...). Adding a second module needs an explicit reason.

## 0002 — MVVM + Repository, with Koin for DI (seed)

**Context.** Need a predictable structure for state, side effects, and dependency wiring across three platforms.
**Decision.** MVVM with Repository pattern. Optional `domain/usecase` layer for reusable or complex business logic. Koin for DI: repositories as `single`, ViewModels as `factory`.
**Consequences.** UI never calls a DataSource directly — strict UI → ViewModel → (UseCase) → Repository → DataSource flow. All new ViewModel/Repository/UseCase code ships with tests in `commonTest`.

## 0003 — `expect/actual` + `RepositoryFactory` bridge for DHIS2 Android SDK (seed)

**Context.** `org.hisp.dhis:android-core` is Android-only, but repository interfaces need to compile on all three targets.
**Decision.** Keep repository *interfaces* in `commonMain`. Provide a real Android implementation, and stub iOS/JVM implementations that return empty data or `LoginResult.Error("... not available on <platform>")`. Wire them through `RepositoryFactory` (`expect object` + per-target `actual object`).
**Consequences.** `RepositoryFactory.initialize(applicationContext)` MUST be called in `MainActivity.onCreate` before `startKoin { ... }`. Adding a repository means adding four files (common interface + three actuals) and registering it in `RepositoryFactory` and `di/KoinModule.kt`. iOS/Desktop are second-class for DHIS2 data flows today.

## 0004 — No navigation library (seed)

**Context.** Only two top-level screens (Login, Home) and two tabs inside Home.
**Decision.** Branch in `App.kt` on `LoginViewModel.uiState`; tab switching inside `HomeScreen` uses a local `HomeTab` enum + `Scaffold` + `NavigationBar`.
**Consequences.** Don't reach for Voyager / Decompose / Compose Navigation yet. Revisit this entry (and supersede it) if we add a third top-level destination or need deep linking.

## 0005 — DHIS2 Mobile UI library over custom components (seed)

**Context.** DHIS2 publishes a Compose Multiplatform design system (`org.hisp.dhis.mobile:designsystem`).
**Decision.** Use the DHIS2 Mobile UI library for UI building blocks instead of writing custom Material components.
**Consequences.** Before rolling a custom composable, check the design system first. Custom components require a justification (something the library cannot express).

## 0006 — `kotlin-test` for multiplatform tests, not JUnit (seed)

**Context.** Tests must run across all KMP targets, not just JVM.
**Decision.** Primary test location is `composeApp/src/commonTest/` using `kotlin-test`. Platform-specific tests live in `androidUnitTest` / `iosTest` / `jvmTest`.
**Consequences.** Don't introduce JUnit-only assertions or runners into shared tests. Test naming: `[ClassName]Test.kt`, methods `should[ExpectedBehavior]When[Condition]()`.

## 0007 — English-only artifacts (seed)

**Context.** Mixed-language teams; want code, comments, commits, and docs to be searchable and reviewable by everyone.
**Decision.** All code, comments, commit messages, and documentation are written in English. Conversations with the agent can be in any language.
**Consequences.** Generated artifacts (including this file and CLAUDE.md) must be English regardless of the conversation language.

## 0008 — Versions centralized in `gradle/libs.versions.toml` (seed)

**Context.** Avoid drifting versions across `build.gradle.kts` files.
**Decision.** Use the Gradle version catalog. Add new dependencies and version bumps there, not inline.
**Consequences.** A PR that adds an inline dependency in `build.gradle.kts` should be sent back to the catalog.

## 0009 — Project keeps a living decision log + evolving CLAUDE.md (2026-05-15)

**Context.** The team is adopting Claude Code as a development harness and wants the project's institutional knowledge to compound across agent conversations instead of being lost when a session ends.
**Decision.** Maintain `DECISIONS.md` (this file) as an append-only log of important decisions. Treat `CLAUDE.md` and `.claude/skills/` as living artifacts that agents are expected to propose updates to when they discover non-obvious project knowledge. Use the `/log-decision` slash command to draft new entries.
**Consequences.** End-of-task reviews include "is there a decision worth logging, a CLAUDE.md update worth making, or a skill worth extracting?" The decision log is the canonical record; agent conversation history is not durable.

## 0010 — On-device LLM via MediaPipe LLM Inference (AI Edge), not ML Kit GenAI / AICore (2026-06-02)

**Context.** Issue #30 integrated an on-device LLM for the Notebook using the ML Kit GenAI Prompt API (`com.google.mlkit:genai-prompt`). That API is bound to **AICore** (Gemini Nano), which only runs on a short allowlist of devices (Pixel 9+, Galaxy S25, …) and never on emulators; our test devices report "unsupported". The original integration was also written against an API surface that does not exist in the published artifact (`GenerativeModel(context, options)`, `PromptClient`, `Tool`/`FunctionDeclaration` function-calling), so it never compiled. The AI Edge Gallery app, by contrast, runs Gemma on those same devices via a different stack.
**Decision.** Swap the Android interpreter backend to the **MediaPipe LLM Inference API** (`com.google.mediapipe:tasks-genai`, the Google AI Edge engine the Gallery uses). It loads a Gemma model file and runs entirely on-device with no AICore dependency, so it works on a wide range of devices and on emulators (the aar ships x86_64 + arm64 native libs). Target model: **Gemma 4 E2B** (`gemma-4-E2B-it.litertlm`). The API is text-in/text-out with no function-calling, so command selection is done by prompting with the command catalog and parsing a strict `CALL`/`CLARIFY` reply through `ToolCallMapper`. The ~2.5 GB model is **not bundled**: it is downloaded on first `warmUp()` into app-private `filesDir`, gated behind a Hugging Face token supplied via `BuildConfig.HF_TOKEN` (read from `local.properties`/env at build time, never committed). Download/storage/OOM errors degrade gracefully to the DSL fallback; `InterpreterState.DownloadingModel(progress)` drives a "Downloading model…" message in the Notebook.
**Consequences.** The AICore path is abandoned for this app. Device support is now governed by RAM/compute (E2B needs ~1.5 GB RAM) rather than an allowlist. A real model download requires a valid `HF_TOKEN`; without it the app silently uses the DSL fallback. The model backend is configurable via the interpreter constructor (model URL/file). Google lists the MediaPipe GenAI route as "maintenance mode" in favor of LiteRT-LM, so a future migration to LiteRT-LM may supersede this entry. Runtime behavior (the `.litertlm` load and prompt-mapping quality) has not yet been verified on a device or emulator.
**Supersedes / Superseded by.** Supersedes the backend choice implied by issue #30. The runtime choice (MediaPipe `tasks-genai`) is superseded by 0013 (LiteRT-LM); the rest of this entry (no AICore, downloaded gated model, `CALL`/`CLARIFY` prompt contract, DSL fallback) still holds.

## 0011 — Resumable, retrying model download with a persisted `.part` file (2026-06-10)

**Context.** With the MediaPipe backend (0010) wired and a valid `HF_TOKEN`, the Notebook still degraded to the DSL fallback ("Natural language unavailable on this device"). Logcat showed the real cause: `ModelDownloader` died mid-transfer with `SocketTimeoutException` while streaming the ~2.5 GB model. The downloader had no retry and no resume — it deleted the partial `.part` on any failure and returned `false`, so a single transient stall threw away the whole download. For a file this large, the probability that *some* read stalls within one uninterrupted pass is high, so it could effectively never complete. The failure was also invisible: `ensureModelDownloaded` swallowed the exception via `runCatching { }.isSuccess` without logging it (unlike `loadModel`).
**Decision.** Make the download robust to transient network failures. (1) Log the download failure (and warm-up start/outcome) so the cause is visible in logcat — the silent swallow was a diagnosability bug. (2) Keep the `.part` file across failures and **resume** with an HTTP `Range: bytes=<already>-` request; honor `206 Partial Content` by appending, and restart cleanly (truncate) if the server answers `200` (ignored the Range). (3) **Retry** a bounded number of times (`MAX_RETRIES = 4`) with linear backoff on `IOException`, without discarding downloaded bytes. (4) Detect short reads by comparing the file length against the expected total and throwing so the retry resumes the remainder. Because the `.part` survives even after retries are exhausted, a later `warmUp()` (reopening the Notebook tab) continues where it left off rather than restarting from zero. The token is still attached only to the `huggingface.co` host, never the redirected CDN.
**Consequences.** Large-model downloads tolerate flaky networks and resume across sessions/process restarts. Tests for `ModelDownloader` live in a new `androidUnitTest` source set (`ModelDownloaderTest`), driven by a local `com.sun.net.httpserver.HttpServer` that simulates a mid-transfer drop and a Range-ignoring server — the first Android-only (`androidUnitTest`) tests in the project. Still open: the `DslFallback` UI banner shows a generic message rather than the captured failure reason (surfacing it requires turning `InterpreterState.DslFallback` into a `data class`, which touches `==` comparisons and existing tests); and the on-device `.litertlm` model load itself remains unverified (see 0010).

## 0012 — Reuse the downloaded model via an external-storage seed (import/export) (2026-06-10)

**Context.** Even with a resumable download (0011), obtaining the ~2.5 GB model takes up to ~20 minutes. The model lives in app-private `filesDir`, which survives normal `adb install -r` redeploys (so day-to-day iteration does *not* re-download) but is wiped on a true uninstall / "clear data" / signing-key change / fresh device. `filesDir` cannot be pre-seeded with `adb push` (it's private and not writable without root), so the only way to repopulate it was the slow network download. We wanted a way to reuse an already-obtained model across clean installs and across developer machines without re-downloading.
**Decision.** Add a model "seed" in app-specific external storage (`context.getExternalFilesDir("llm")`). `warmUp()` resolves the model in priority order: already in `filesDir` → **import** (copy) from the external seed → download. After a successful load it **exports** the `filesDir` model back to the external seed if that copy is missing or a different size. Both steps are best-effort (failures are logged, never block readiness or crash). Chose the app-specific external dir over public `Downloads` deliberately: it needs **no storage permission** (avoiding scoped-storage / `MANAGE_EXTERNAL_STORAGE` friction), at the cost of also being wiped on uninstall — but unlike `filesDir` it is `adb push`-able, so re-seeding is a fast local copy. Surviving uninstall outright would require public storage + permissions, which we judged not worth it for a dev playground.
**Consequences.** After the first download, the app auto-copies the model to `/sdcard/Android/data/<pkg>/files/llm/`; developers can `adb pull` it once and `adb push` it back after any wipe (or onto a teammate's device) to skip the download — the app imports it on next `warmUp()`. The external seed is *not* an uninstall-survival mechanism (it is also wiped); its value is being cheaply re-seedable. The one-time import/export is a multi-GB local copy that runs during warm-up while the UI shows "Loading language model…" (no dedicated progress state was added). The seed is validated only by non-empty length / size match, not a checksum, so a truncated `adb push` could be imported and then fail to load (degrading to download/DSL fallback). Public `Downloads` + permission remains the path if true uninstall survival is ever needed.

## 0013 — Run the on-device LLM on LiteRT-LM, not MediaPipe `tasks-genai` (2026-06-10)

**Context.** With the MediaPipe `tasks-genai` backend (0010) finally working end-to-end, Notebook inference took ~2 minutes per request on a capable physical phone. Logcat showed why: LiteRT (the runtime under `tasks-genai`) tried and failed to register every GPU/NPU accelerator (`GPU accelerator could not be loaded and registered`) and fell back to CPU. Forcing `setPreferredBackend(GPU)` made it worse — on devices/emulators without a usable OpenCL driver the GPU path crashes the process natively (uncatchable `SIGSEGV` in `libllm_inference_engine_jni`), not a catchable exception. Adding the `<uses-native-library>` manifest entries the AI Edge Gallery declares (`libOpenCL.so`, `libvndksupport.so`, `libcdsprpc.so`, `libedgetpu_litert.so`) was necessary but not sufficient. The decisive finding: the Gallery — which runs the *same* `.litertlm` model fast on the *same* phone — does **not** use `com.google.mediapipe:tasks-genai`. It uses `com.google.ai.edge.litertlm:litertlm-android`, the runtime the `.litertlm` format is built for; `tasks-genai` is in Google's "maintenance mode."
**Decision.** Swap the Android interpreter runtime to **`com.google.ai.edge.litertlm:litertlm-android`** (0.11.0). Rewrite the engine path against its API: `Engine` + `EngineConfig(modelPath, backend, cacheDir)` with `Backend.GPU()` preferred and a `Backend.CPU()` fallback (emulators are forced to CPU up-front via a build-fingerprint check, since GPU there is the known native-crash case); the command catalog + reply-format rules become the `Conversation`'s `systemInstruction` (the runtime applies the model's chat template and stop tokens, so the manual `<start_of_turn>` markers from the `tasks-genai` era are gone); the response is streamed via the cancellable `Flow<Message>` from `sendMessageAsync`, which makes `withTimeout` actually abort a slow generation (the `tasks-genai` blocking/`ListenableFuture` path could not be interrupted and required `cancelGenerateResponseAsync` to release an engine lock). Keep the manifest `<uses-native-library>` entries (LiteRT-LM still dlopens the vendor OpenCL driver). Download/seed/sanitize/`CALL`-`CLARIFY` parsing are unchanged. The published API differed from the docs in places (verified against the AAR with `javap`: `Message.contents.contents` → `Content.Text.text`; `SamplerConfig` uses `Double`).
**Consequences.** GPU now initializes on device and inference drops from ~2 minutes to seconds; CPU remains the safe fallback (and the only path on emulators). The dependency on `tasks-genai` is removed. `MODEL_FILE_NAME`/URL are unchanged, so the already-downloaded/seeded model is reused as-is. Cancellation is now clean (Flow-based), removing the engine-lock hazard. Open follow-up: the streamed `Message` chunks are assumed to be deltas (concatenated); if a future version emits cumulative messages this would duplicate text. CLAUDE.md's "On-device LLM" section should be updated to say LiteRT-LM rather than MediaPipe.
**Supersedes / Superseded by.** Supersedes the runtime choice in 0010 (MediaPipe `tasks-genai`). The rest of 0010 still stands.
