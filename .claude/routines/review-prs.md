# Autonomous PR-Reviewer Routine Instructions

This file is read by the Claude.ai Routine that automatically picks up open pull requests,
runs a code review using the official Claude code review plug-in, and posts the result.

## Repository

`taridepaco/dhis2-multiplatform-mobile-playground`

## What the Routine Does (one run)

The routine is triggered by a GitHub webhook that carries the **triggering pull request number**.

1. Read the triggering pull request using the PR number from the webhook context.
2. Check whether a review has already been posted by this routine (look for an existing
   review or review comment authored by the bot on this PR).
3. If a review already exists from this routine, stop — no further action needed.
4. Run the code review using the `/review` skill (powered by `code-review@claude-plugins-official`).
5. Post the review results as a pull request review on GitHub.
6. Stop.

## Architecture Rules

For more information about architecture, design patterns, and code conventions go to CLAUDE.md.

## Review Scope

The review must cover:

- **Correctness** — logic errors, off-by-ones, null-safety issues, incorrect state handling.
- **Architecture compliance** — adherence to MVVM + Repository pattern, proper layer boundaries
  (UI → ViewModel → UseCase → Repository → DataSource), and Koin DI wiring.
- **KMP conventions** — no Android-only APIs leaking into `commonMain`; `expect/actual` used only
  when platform-specific APIs force it; stub `actual` implementations provided for iOS/JVM.
- **State management** — `StateFlow<UiState>` exposed correctly, no business logic in Composables,
  sealed result hierarchies used instead of exceptions across layers.
- **Tests** — new ViewModel / Repository / UseCase code ships with tests in `commonTest/`;
  naming follows `should[ExpectedBehavior]When[Condition]()`.
- **DHIS2 UI components** — DHIS2 Mobile UI design system (`org.hisp.dhis.mobile:designsystem`)
  used instead of custom components where applicable.
- **Security** — no injection vulnerabilities, credentials not hardcoded, no sensitive data
  logged or exposed to the UI.
- **Code quality** — no unnecessary comments, no over-engineering beyond what the task requires,
  English-only artifacts.

## Review Output Format

Post the review as a **GitHub pull request review** (not a plain comment) so it appears in the
PR's review timeline. Use the following structure:

```
## Code Review

### Summary
<1-3 sentences on overall quality and the main findings>

### Issues
<!-- List only real problems; omit this section if there are none -->
- **[Severity: Critical/Major/Minor]** `path/to/file.kt:line` — description

### Suggestions
<!-- Optional improvements that are not blockers -->
- `path/to/file.kt:line` — description

### Verdict
<!-- One of: APPROVE / REQUEST_CHANGES / COMMENT -->
<verdict and one-line rationale>
```

Severity guide:
- **Critical** — correctness bug, security issue, or broken build.
- **Major** — architectural violation or missing required tests.
- **Minor** — style or naming deviation.

## What to Do When the PR Is a Draft

If the pull request is in **draft** state, skip it silently — no review is needed until the
author marks it ready for review.
