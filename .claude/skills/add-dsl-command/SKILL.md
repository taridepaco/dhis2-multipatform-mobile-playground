---
name: add-dsl-command
description: Add a new command to the Notebook DSL. Use when the user asks to add, register, or implement a new notebook command, a new `d2.*` operation, or a new debugging command for the DHIS2 SDK. Produces a self-describing CommandHandler that is immediately consumable by a future local-LLM agent via the catalog export. Examples of triggers: "add a d2.events.list command", "expose d2.organisationUnits.tree in the notebook", "register a new DSL command for X".
---

# Add a new DSL command

The Notebook DSL (see `composeApp/src/commonMain/.../dsl/`) is a small, self-describing command language used to debug the DHIS2 Android SDK (`D2`) on device. Its design goal is **dual-use**: a human types a command into the Notebook cell, and a local LLM agent will — in a later iteration — consume the same registry as its toolset. Every command you add is therefore both a UI feature and a "tool" for the future agent.

## Invariants you must preserve

1. **One file per command**. Keeps the registry scannable and makes per-command review trivial.
2. **Natural-language descriptions live next to the code**. They *are* the documentation — do not split them into a separate markdown file; they will drift.
3. **All strings shown to the LLM / user are in English** (per the repo language policy).
4. **Read-only by default**. `readOnly = true` unless the command mutates remote/local state (sync, logout, delete, write). The flag is non-optional — set it explicitly.
5. **Common-code pure where possible**. The `CommandSpec` lives in `commonMain`; only the `CommandHandler` that calls into `D2` lives in `androidMain`. iOS/JVM stubs must not regress.
6. **No new platform-specific dependencies** without checking `gradle/libs.versions.toml` first.

## Checklist (follow in order)

### 1. Decide the command shape

- Name: dotted, lowercased, action-oriented. Format: `d2.<module>.<verb>` (e.g. `d2.events.list`, `d2.programs.get`, `d2.system.info`). Built-ins without the `d2.` prefix (`help`, `describe`, `commands`) are reserved.
- Parameters: each needs `name`, `type` (`string` | `int` | `bool`), `description` (NL), `required`. Keep arity small (≤ 3). If you need more, consider whether this is two commands.
- Return shape: what the JSON will look like. Prefer stable field names — the LLM will key off them.
- Safety: does it mutate anything? If yes, `readOnly = false` and expect a future confirmation gate in the UI.

### 2. Add the `CommandSpec`

In `composeApp/src/commonMain/.../dsl/catalog/BuiltInCommands.kt` (for platform-agnostic specs) or inline in the handler file — follow whichever convention is already in use when you read the registry. Required fields, in this order:

```kotlin
CommandSpec(
    name = "d2.events.list",
    description = "List events for the current user, optionally filtered by program UID. Returns up to `limit` rows.",
    parameters = listOf(
        ParamSpec(name = "programUid", type = "string", description = "Restrict to events of this program. Omit for all.", required = false),
        ParamSpec(name = "limit", type = "int", description = "Maximum number of rows. Defaults to 100, max 500.", required = false),
    ),
    examples = listOf(
        "d2.events.list",
        "d2.events.list(programUid = \"IpHINAT79UW\")",
        "d2.events.list(limit = 10)",
    ),
    readOnly = true,
    returns = "Array of events, each with eventUid, programUid, orgUnitUid, eventDate, status.",
)
```

**Writing the `description` and `returns` well is the single most important step** for LLM usability. Say what the command does in one sentence, name the real-world concept (not the SDK method), and be precise about what comes back. Assume the model has never seen the SDK.

### 3. Implement the handler

Create `composeApp/src/androidMain/.../dsl/commands/<Name>Command.kt`. Mirror the style of sibling commands in that directory — specifically the defensive preamble:

```kotlin
val d2 = D2Manager.getD2()
    ?: return DslResult.Error("D2 is not initialized — log in first.")
```

Then `withContext(Dispatchers.IO) { ... }` around any `blockingGet()` / `blockingCount()` call. The executor already applies a 10 s `withTimeout` — do not add another one.

Build the result via `ResultFormatter` (never hand-roll JSON):

```kotlin
return ResultFormatter.success(
    data = events.map { it.toSerializableShape() },
    display = events.joinToString("\n") { "${it.uid()}  ${it.eventDate()}" },
)
```

Keep the `display` field short and scannable — it's what the user sees in the cell. Keep the JSON `data` structurally stable — the LLM will parse it.

### 4. Register the handler

Add a single entry to the registry assembly site (`CommandRegistry` / its Koin binding — look for where siblings are registered). A command that isn't registered is invisible to both the parser and the LLM catalog.

### 5. Stubs on non-Android targets

You do **not** need to touch `iosMain` / `jvmMain`. The per-target `StubDslExecutor` already rejects all commands uniformly with "not available on this platform". Only edit those stubs if the command is somehow platform-portable (rare — the SDK is Android-only).

### 6. Tests (`composeApp/src/commonTest/`)

Follow the naming convention `should[Behavior]When[Condition]()`. At minimum:

- `should return parse error when required parameter is missing`
- `should return spec via describe command`
- `should appear in the catalog JSON emitted by commands`

If the handler has any non-trivial argument parsing or shaping logic, extract that into a pure common-code function and test it in `commonTest` against a fake `D2`-like interface — do not test against the real SDK from commonTest.

### 7. Smoke-verify

```
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat :composeApp:assembleDebug
```

Install, log in, open the Notebook tab, and type:
- `describe d2.<module>.<verb>` — should show your new spec.
- `commands` — JSON catalog should include your entry with all fields populated.
- The command itself with representative arguments.

## Anti-patterns to avoid

- **Silently swallowing `Exception`**. Return `DslResult.Error(e.message ?: "Unknown error")` — errors must be visible to both the user and the future LLM so it can recover.
- **Coupling to the UI**. Handlers must never reference Compose, `Context`, or ViewModel types. The LLM path will call them with no UI present.
- **Non-deterministic JSON keys** (e.g. depending on object hash ordering). Breaks LLM prompting.
- **Huge unbounded result sets**. Always enforce a `limit` default on collection commands.
- **Leaking `blockingGet()` call chains to callers**. Handlers are the bottom of the stack — they consume the SDK's blocking API and return a `DslResult`.
- **Adding the same command twice under different names** (e.g. `d2.programs.list` and `d2.programs.all`). The registry detects duplicates; pick one and delete the other.

## If you're unsure

Read three existing siblings in `composeApp/src/androidMain/.../dsl/commands/`. The convention is deliberate — copy it rather than invent.
