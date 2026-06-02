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
**Supersedes / Superseded by.** Supersedes the backend choice implied by issue #30.
