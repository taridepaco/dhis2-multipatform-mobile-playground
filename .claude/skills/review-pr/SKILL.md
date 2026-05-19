---
name: review-pr
description: Review a pull request in taridepaco/dhis2-multiplatform-mobile-playground. Use when you need to run a code review on a specific PR, check its quality against the project's architecture and conventions, and post the result as a GitHub PR review. Covers correctness, MVVM architecture compliance, KMP conventions, state management, tests, DHIS2 UI components, security, and code quality. Examples of triggers: "review PR #42", "post a review on the open pull request", "check PR #7 for issues".
---

# Review a Pull Request

This skill reviews a pull request in `taridepaco/dhis2-multiplatform-mobile-playground` and posts
the result as a GitHub PR review.

## Repository

`taridepaco/dhis2-multiplatform-mobile-playground`

## Steps

1. Read the pull request using the PR number provided. Fetch its metadata (title, body, state,
   changed files) via `mcp__github__pull_request_read`.
2. Check whether a review has already been posted by this routine (look for an existing review or
   review comment authored by the bot on this PR).
3. If a review already exists from this routine, stop — no further action needed.
4. If the PR is in **draft** state, skip it silently — no review needed until the author marks it
   ready for review.
5. Read the changed files. Use `mcp__github__get_file_contents` or `mcp__github__pull_request_read`
   (method: `list_files`) to inspect the diff.
6. Evaluate the changes against the Review Scope below.
7. Post the result as a **GitHub pull request review** following the Output Format below.

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
