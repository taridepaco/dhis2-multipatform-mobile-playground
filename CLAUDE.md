# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Kotlin Multiplatform (KMP) + Compose Multiplatform app targeting **Android**, **iOS**, and **Desktop (JVM)**. All shared code lives in `composeApp/` (single Gradle module).

**Language policy:** all code, comments, commit messages, and documentation must be in **English**. Conversations with the agent can be in any language, but generated artifacts are English.

## Build / run / test commands

Use `./gradlew` on macOS/Linux, `.\gradlew.bat` on Windows (this workspace is Windows).

| Task | Command |
| --- | --- |
| Android debug APK | `.\gradlew.bat :composeApp:assembleDebug` |
| Desktop (JVM) run | `.\gradlew.bat :composeApp:run` |
| iOS | Open `iosApp/iosApp.xcodeproj` in Xcode and run from there (no gradle task) |
| All tests | `.\gradlew.bat :composeApp:allTests` |
| Shared (common) tests only | `.\gradlew.bat :composeApp:jvmTest` (runs `commonTest` on the JVM target) |
| Single test class | `.\gradlew.bat :composeApp:jvmTest --tests "org.dhis2.multiplatformmobileplayground.viewmodel.LoginViewModelTest"` |
| Single test method | append `.methodName` to the `--tests` filter |
| Android unit tests | `.\gradlew.bat :composeApp:testDebugUnitTest` |

No lint/format task is configured — stick to default Kotlin style (`kotlin.code.style=official` in `gradle.properties`).

Dependencies and versions are centralized in `gradle/libs.versions.toml` (version catalog). Add new libs there, not inline in `build.gradle.kts`.

## Architecture

**Pattern:** MVVM + Repository, with Koin for DI. Optional domain layer (`UseCase`) for complex or reusable business logic. Principles: **Single Source of Truth** (each data type has one owner that exposes immutable data), **Unidirectional Data Flow** (state down, events up), UI driven by persistent data models.

### The `expect/actual` + `RepositoryFactory` bridge

The DHIS2 Android SDK is **Android-only**. The codebase handles this by keeping repository *interfaces* in `commonMain` and providing three implementations:

- `androidMain` — real implementation using `org.hisp.dhis:android-core` (`D2Manager`, `D2` etc.).
- `iosMain` / `jvmMain` — stubs that return empty data or `LoginResult.Error("... not available on <platform>")`. These exist so the common code compiles and the non-Android targets still build/run.

The glue is `RepositoryFactory` — an `expect object` in `commonMain/.../data/repository/RepositoryFactory.kt` with per-platform `actual object` implementations. **The Android actual is stateful**: `RepositoryFactory.initialize(applicationContext)` must be called from `MainActivity.onCreate` *before* `startKoin { ... }`, because Koin's `appModule` resolves repositories by calling `RepositoryFactory.create*()`, which depends on that context. If you add a new repository, add all four files (common interface + three platform actuals) and wire it into both `RepositoryFactory` and `di/KoinModule.kt`.

Keep as much code as possible in `commonMain`; reach for `expect/actual` only when a platform-specific API (like the DHIS2 SDK) forces it.

### DI wiring

- Common: `di/KoinModule.kt` declares `appModule` (repositories as `single`, ViewModels as `factory`).
- Android: `MainActivity` calls `RepositoryFactory.initialize(...)` then `startKoin { androidContext(...); modules(appModule) }`.
- iOS/JVM entry points (`MainViewController.kt`, `jvmMain/main.kt`) currently launch `App()` directly — if you add Koin init for those targets, do it before `App()` is composed. `App.kt` wraps everything in `KoinContext { ... }` and resolves VMs with `koinViewModel()`.

### Navigation

There is no navigation library. `App.kt` branches on `LoginViewModel.uiState` (`isCheckingAuth` / `isLoginSuccessful`) to pick `LoginScreen` vs `HomeScreen`. `HomeScreen` uses a local `HomeTab` enum + `Scaffold` + `NavigationBar` for tab switching (Home/Notebook). Keep this in mind before reaching for a nav library.

## DHIS2-specific rules

- **UI components:** use the **DHIS2 Mobile UI** library (`org.hisp.dhis.mobile:designsystem`) instead of rolling custom components. Docs: <https://developers.dhis2.org/docs/mobile/mobile-ui/overview/>
- **Android data/business logic:** use the **DHIS2 Android SDK** (`org.hisp.dhis:android-core`). Docs: <https://github.com/dhis2/dhis2-android-sdk/tree/master/docs/content/developer>
- **iOS / Desktop:** stub / no-op `actual` implementations are acceptable (the SDK is Android-only today).

## On-device LLM (Notebook natural-language input)

The Notebook lets users type natural language; an on-device LLM maps it to a DSL command. This is **Android-only** and built on the **LiteRT-LM** runtime (Google AI Edge, `com.google.ai.edge.litertlm:litertlm-android`) — the runtime the AI Edge Gallery uses for `.litertlm` models. It runs entirely on-device, preferring GPU with a CPU fallback. We deliberately do **not** use ML Kit GenAI / AICore (gated to a device allowlist, won't run on emulators); we also moved off MediaPipe `tasks-genai`, whose GPU accelerator wouldn't load on real hardware (CPU-only, ~2 min/request). See `DECISIONS.md` 0010 and 0013.

- **Interpreter chain:** `NotebookViewModel` → `InputResolver` (`LlmInputResolver` on Android, `DslInputResolver` elsewhere) → `NaturalLanguageInterpreter`. The Android implementation is `dsl/llm/Gemma4NaturalLanguageInterpreter`; iOS/JVM use no-op `UnavailableInterpreter` stubs wired via `NaturalLanguageInterpreterFactory` (the same `expect/actual` factory pattern as repositories).
- **Runtime & backend:** uses LiteRT-LM's `Engine`/`Conversation`. `loadModel()` prefers `Backend.GPU()` and falls back to `Backend.CPU()`; **emulators are forced to CPU** (GPU there has no OpenCL and crashes the process natively — an uncatchable SIGSEGV, so it must be avoided up-front, not caught). GPU on real devices requires the `<uses-native-library>` entries in `AndroidManifest.xml` (`libOpenCL.so`, …) so Android 12+ lets LiteRT `dlopen` the vendor driver. The `Conversation` applies the model's chat template, so don't add manual `<start_of_turn>` markers to the prompt.
- **No function-calling:** inference is text-in/text-out. Command selection works by giving the model the command catalog as the `Conversation`'s `systemInstruction` and asking it to reply in a strict `CALL <command>` / `CLARIFY: …` format, parsed and routed through `ToolCallMapper`. Keep that response contract in sync between `buildSystemInstruction` and `parseResponse`. `parseResponse` is deliberately tolerant of common model slips: a missing `CALL` prefix (accepted if the first token is a registered command), `<…>`/backtick/paren markup around the command name, and mangled UIDs (LLMs don't copy random 11-char DHIS2 UIDs reliably, so a UID-shaped arg is recovered verbatim from the user's input).
- **Model:** Gemma 4 E2B (`gemma-4-E2B-it.litertlm`, ~2.5 GB). Not bundled — fetched into app-private `filesDir` on first `warmUp()`. The download (`ModelDownloader`) is **resumable + retrying**: it keeps a `<model>.part` file across failures, resumes with an HTTP `Range` request, and retries with backoff, so a transient stall doesn't restart the multi-GB transfer (see `DECISIONS.md` 0011).
- **Model seed (reuse across reinstalls):** `warmUp()` resolves the model in priority order — already in `filesDir` → **import** a copy from app-specific external storage (`getExternalFilesDir("llm")`) → download. After a successful load it **exports** the model back to that external dir if missing. Both copies are wiped on uninstall, but the external dir is `adb push`-able, so re-seeding is a fast local copy instead of a 20-min download. External path: `/sdcard/Android/data/org.dhis2.multiplatformmobileplayground/files/llm/`. To reuse the model across clean installs/devices: `adb pull` it once, then `adb push` it back into that dir and launch — the app imports it.
- **Hugging Face token:** the model is gated. The download needs a token exposed as `BuildConfig.HF_TOKEN`, populated from `HF_TOKEN` in `local.properties` (gitignored) or the `HF_TOKEN` env var. Without it, download fails and the app degrades to the DSL fallback. Never commit a token.
- **Warm-up & state:** `warmUp(onProgress)` reports progress via `InterpreterState` (`Loading` → `DownloadingModel(progress)` → `Ready` / `DslFallback`). Download, storage, and out-of-memory errors are caught and degrade to `DslFallback` rather than crashing. If you add a warm-up phase, surface it through `InterpreterState` so the Notebook input placeholder reflects it.

## Conventions

### Package layout (under `org.dhis2.multiplatformmobileplayground`)

```
ui/screens         Composable screens ([Feature]Screen.kt)
ui/components      Reusable composables
viewmodel          [Feature]ViewModel.kt
domain/usecase     [Action][Entity]UseCase.kt  (optional)
data/repository    [Entity]Repository.kt (+ Impl per platform)
data/datasource    [Entity][Type]DataSource.kt
model              Data/UI state classes
di                 Koin modules
```

### State & ViewModels

- Expose UI state as `StateFlow<UiState>` — private `MutableStateFlow`, public `asStateFlow()`; combine multiple flows with `combine()` when needed.
- No business logic in Composables. Prefer stateless composables + state hoisting; use `collectAsState()` to observe.
- For operation results, prefer a sealed hierarchy (e.g. `LoginResult.Success` / `LoginResult.Error`) over throwing across layers.

### Tests

- Primary location: **`composeApp/src/commonTest/`**, using `kotlin-test` (not JUnit) so they stay multiplatform. Platform-specific tests go in `androidUnitTest` / `iosTest` / `jvmTest` source sets.
- Test class naming: `[ClassName]Test.kt`. Test method naming: `should[ExpectedBehavior]When[Condition]()`.
- All new ViewModel / Repository / UseCase code should ship with tests.

### Layer boundaries

UI → ViewModel → (UseCase) → Repository → DataSource. Don't skip layers (e.g. a Composable calling a DataSource directly). Repositories are the single source of truth for their data type.

## Living documentation

This project treats its agent-facing documentation as a living artifact. After completing a non-trivial task, take a moment to ask:

1. **Was a decision made?** A decision is anything with future implications — a chosen approach, a constraint accepted, a library picked, a pattern ruled in or out. If yes, propose a new entry for `DECISIONS.md` (or run `/log-decision`). Bug fixes, refactors, and routine implementations are usually *not* decisions.
2. **Did you discover a convention or gotcha future agents would need to know?** If yes, propose an edit to this `CLAUDE.md` rather than leaving the knowledge in conversation history. Add it to the most relevant existing section; only add new sections when no existing one fits.
3. **Did you repeat a multi-step workflow that might come up again?** If you find yourself doing the same kind of task two or three times (e.g. "add a new repository: four files + factory + Koin"), propose extracting it as a project skill in `.claude/skills/`. Keep skill scope narrow and the description specific so it triggers reliably.

Propose these updates — do not make them silently as a side effect of another task. The user should be able to review the proposed change separately from the main work.

The canonical record of decisions is `DECISIONS.md`. Agent conversation history is not durable; do not rely on it for institutional knowledge.
