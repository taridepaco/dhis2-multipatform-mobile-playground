---
name: review-pr
description: Review a pull request in taridepaco/dhis2-multiplatform-mobile-playground, apply fixes directly to the PR branch, and post a summary comment. Use when you need to automatically fix issues in a PR (correctness, architecture compliance, KMP conventions, state management, tests, DHIS2 UI, security, code quality) and summarize what was changed. Examples of triggers: "review and fix PR #42", "apply fixes to the open pull request", "auto-fix PR #7".
---

# Review and Fix a Pull Request

This skill reviews a pull request in `taridepaco/dhis2-multiplatform-mobile-playground`, applies
fixes directly to the PR branch by committing and pushing, and posts a plain comment summarising
what was changed.

## Repository

`taridepaco/dhis2-multiplatform-mobile-playground`

## Steps

1. Read the pull request using the PR number provided. Fetch its metadata (title, body, state,
   base branch, head branch, changed files) via `mcp__github__pull_request_read`.
2. Check whether this skill has already run on this PR (look for a bot comment that starts with
   `## Fix Summary`). If one exists, stop — no further action needed.
3. If the PR is in **draft** state, skip it silently — no action needed until the author marks it
   ready for review.
4. Check out the PR's head branch locally and read the changed files in full.
5. Evaluate the changes against the Review Scope below. Identify every issue that can be fixed
   safely without guessing at missing requirements.
6. Apply the fixes directly in the local working copy. Commit each logical fix as a separate
   commit on the PR's head branch with a clear message. Push the commits to the remote branch.
7. Post a **plain pull request comment** (not a PR review) summarising the changes, using the
   Output Format below.
8. If no fixable issues are found, post a comment confirming the PR looks good and no changes
   were needed.

## Review Scope

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
- **Code quality** — no unnecessary comments, no over-engineering, English-only artifacts.

## Output Format

Post a plain comment on the PR with the following structure:

```
## Fix Summary

### Changes applied
<!-- List each fix as a bullet; omit section if nothing was changed -->
- `path/to/file.kt` — what was fixed and why

### No action needed
<!-- List items inspected and found correct; omit section if everything was fixed -->
- `path/to/file.kt` — reason it was left unchanged

### Notes
<!-- Any caveats, ambiguous items left for human review, or follow-up suggestions -->
```

If no issues were found at all, post:

```
## Fix Summary

No fixable issues found. The PR looks good as-is.
```
