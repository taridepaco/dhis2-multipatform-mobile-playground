# Autonomous Issue-Solver Routine Instructions

This file is read by the Claude.ai Routine that automatically picks up open GitHub issues,
implements solutions, and opens pull requests.

## Repository

`taridepaco/dhis2-multiplatform-mobile-playground`

## What the Routine Does (one run)

The routine is triggered by a GitHub webhook that carries the **triggering issue number**.

1. Read the triggering issue using the issue number from the webhook context.
2. Check whether a pull request already references it (search open PRs for the issue number
   in the title or body, and check for branches named `fix/issue-{number}`).
3. If a linked PR already exists, stop — no further action needed.
4. Implement the solution for the triggering issue following the rules below.
5. Commit the changes and open a pull request.
6. After the PR is created, spawn a **subagent** to review it. The subagent must be isolated — do
   not share the current conversation context with it. Pass to the subagent:
   - The PR number that was just created.
   - The full contents of `.claude/skills/review-pr/SKILL.md` as its operating instructions.
   The subagent should follow those instructions to read the PR, evaluate it, and post a GitHub
   PR review. It must not perform any other action.
7. Stop.

## Architecture Rules

For more information about architecture, design patterns and code conventions go to CLAUDE.md

## Branch and PR Format

- Branch name: `fix/issue-{number}-{short-slug}` (e.g., `fix/issue-42-login-crash`)
- PR title: `Fix #{number}: {issue title}` (e.g., `Fix #42: Login screen crashes on empty password`)
- PR body must include:
    - `Closes #{number}` so GitHub auto-links the issue
    - A short summary of what changed and why
    - Claude Skills used to implement the solution (e.g., "Used `add-dsl-command` skill to add a new command for listing events.")
    - Confirmation that `./gradlew :composeApp:jvmTest` passed

## What to Do When the Issue Is Ambiguous

If the issue lacks enough information to implement a safe, correct solution, open the PR as a
**draft** and leave a comment on the issue asking for clarification. Do not guess at requirements
that could break existing behavior.
