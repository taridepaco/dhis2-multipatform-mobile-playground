# Feature Development Framework — Proposal

**Status:** Proposed — for team review. Nothing here is implemented yet; this document is the
design we want to agree on before building it.

## Goal

One repeatable pipeline that takes a feature *idea* from a conversation all the way to merged
code, driven by an in-session **orchestrator** (the framework itself) rather than by webhooks,
with exactly two human gates:

1. **Validate the plan** before any issue is created.
2. **Review and approve each pull request** before it merges.

## Why

We already have a working "back-half" for turning issues into reviewed PRs, but it is wired to
**webhooks** (Claude.ai Routines triggered by GitHub events), and we have no "front-half" for
turning a feature idea into a validated plan and issues.

We want to:

- Add the front-half: idea → clarifying questions → validated plan → GitHub issues.
- **Remove the webhook trigger** for solving/reviewing. The orchestrator should start and run the
  issue-solving phase itself, so the whole flow lives in one place and one run.

## Current building blocks

| Piece | Today | In this proposal |
| --- | --- | --- |
| `.claude/routines/solve-issues.md` | Webhook: issue → implement → PR → self-review | **Removed**; logic becomes the `solve-issue` skill |
| `.claude/routines/review-prs.md` | Webhook: PR → post review | **Removed**; review folded into the orchestrated flow via `review-pr` |
| `.claude/skills/review-pr/SKILL.md` | Review + fix + comment | **Kept**, reused for self-review |
| `scripts/auto-merge.sh` + `.github/workflows/auto-merge.yml` | Squash-merge on green CI | **Kept**, now gated on a human approval |
| Front-half (idea → plan → issues) | Does not exist | **New** orchestrator + skills |

## Target pipeline (orchestrator-driven, no webhooks)

```
conversation (feature idea)
  → /plan-feature  (ORCHESTRATOR): gathers full project context + asks clarifying questions
  → validated feature plan                         ── HUMAN GATE 1: validate the plan ──
  → orchestrator creates GitHub issues (epic + sub-issues)
  → orchestrator drives the solving phase  [fully autonomous — no per-issue human step, no webhook]:
        for each child issue → spawn an isolated subagent running the `solve-issue` skill:
             read issue → branch → implement → :composeApp:jvmTest → open PR → self-review (review-pr)
  → PRs open                                       ── HUMAN GATE 2: review + approve each PR ──
  → auto-merge.sh merges each PR on human approval + green CI
```

### What "fully autonomous" means here

Once the plan is validated, the orchestrator creates the issues **and** solves them all **and**
opens the PRs within the same run — no per-issue human step, and no webhook starts it: the
framework does. The two gates above are the only human touchpoints.

## Proposed changes

### Create

- **`.claude/skills/plan-feature/SKILL.md`** — the orchestrator (source of truth). Auto-triggers
  on a feature idea and is also invoked by the command. Flow: take the idea → gather context
  (`CLAUDE.md`, `DECISIONS.md`, relevant code) → ask clarifying questions (scope, platforms
  Android/iOS/JVM, layer, acceptance criteria, non-goals) → present a feature plan and **stop
  (Gate 1)** → on validation, create issues via `create-feature-issues` → drive the solving phase
  by spawning one isolated subagent per child issue seeded with `solve-issue`.
- **`.claude/commands/plan-feature.md`** — a thin `/plan-feature` entry point that invokes the
  `plan-feature` skill for the described feature.
- **`.claude/skills/create-feature-issues/SKILL.md`** — decomposition + issue creation. One epic
  issue plus one sub-issue per independently shippable unit (single issue when the feature is
  small). Each child carries a consumable contract: title, description, acceptance-criteria
  checklist, affected files/areas, target platforms, test expectations, and likely-applicable
  skills. Applies a `feature` label (and `epic` on the parent).
- **`.claude/skills/solve-issue/SKILL.md`** — the de-webhooked single-issue solver (the old
  `solve-issues.md` logic without the webhook framing), invoked per issue by the orchestrator:
  branch `feat/issue-{n}-{slug}` (label-aware, `fix/…` fallback) → implement per `CLAUDE.md` →
  `:composeApp:jvmTest` → open PR (`Closes #{n}` + epic link + skills used) → self-review via
  `review-pr`.

### Modify

- **`scripts/auto-merge.sh`** — require a human `APPROVED` review (bots excluded) before merging,
  in addition to the existing green-CI / clean-mergeable checks. This makes Gate 2 real.
- **`CLAUDE.md`** — add a "Development framework / feature pipeline" section documenting the flow
  and the two gates.
- **`DECISIONS.md`** — record adopting the orchestrator-driven pipeline, retiring the webhook
  routines, and requiring human approval before merge.

### Remove

- **`.claude/routines/solve-issues.md`** and **`.claude/routines/review-prs.md`** (and the emptied
  `.claude/routines/` directory) — their logic moves into the orchestrated skills.

## Open decisions for the team

1. **Entry point** — proposed: a `/plan-feature` command **plus** a thin auto-triggering skill.
   Alternative: command-only.
2. **Issue structure** — proposed: epic + native GitHub sub-issues, collapsing to a single issue
   when small. Alternatives: flat linked issues, or one tracking issue with a checklist.
3. **Merge gate** — proposed: require human approval before merge (Gate 2). Alternative: keep
   auto-merge on green CI for true end-to-end autonomy (drops Gate 2).
4. **Webhook cleanup** — the existing Claude.ai Routines / GitHub webhooks are configured outside
   the repo; removing these files does not disable them. We'll need to disable those Routines so
   they stop firing alongside the new orchestrator.

## Verification (once implemented)

1. Dry-run `/plan-feature` on a small idea → it interviews, prints a plan, and stops before
   creating issues (Gate 1).
2. After validation → an epic + sub-issues appear with the `feature` label and correct links.
3. Orchestrated solving → a solver subagent per child opens a PR that `Closes` its issue and links
   the epic; CI (`:composeApp:jvmTest`) runs. No webhook involved.
4. Merge gate → a PR with green CI but no human approval stays open; after a human `APPROVED`
   review, `auto-merge.sh` squash-merges it.
