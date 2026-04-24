# Autonomous Issue-Solver Routine Instructions

This file is read by the Claude.ai Routine that automatically picks up open GitHub issues,
implements solutions, and opens pull requests.

## Repository

`taridepaco/dhis2-multiplatform-mobile-playground`

## What the Routine Does (one run)

1. List all open issues in the repository.
2. For each open issue, check whether a pull request already references it (search open PRs for
   the issue number in the title or body, and check for branches named `fix/issue-{number}`).
3. Skip issues that already have a linked PR.
4. Pick the oldest unaddressed issue.
5. Implement the solution following the rules below.
6. Commit the changes and open a pull request.
7. Stop — the next scheduled run will repeat from step 1.

## Architecture Rules

For more information about architecture, design patterns and code conventions go to CLAUDE.md

## Branch and PR Format

- Branch name: `fix/issue-{number}-{short-slug}` (e.g., `fix/issue-42-login-crash`)
- PR title: `Fix #{number}: {issue title}` (e.g., `Fix #42: Login screen crashes on empty password`)
- PR body must include:
    - `Closes #{number}` so GitHub auto-links the issue
    - A short summary of what changed and why
    - Confirmation that `./gradlew :composeApp:jvmTest` passed

## What to Do When the Issue Is Ambiguous

If the issue lacks enough information to implement a safe, correct solution, open the PR as a
**draft** and leave a comment on the issue asking for clarification. Do not guess at requirements
that could break existing behavior.