---
description: Draft a new entry for DECISIONS.md based on the current conversation.
---

The user wants to log a decision taken in this conversation to `DECISIONS.md` at the repo root.

Steps:

1. Read `DECISIONS.md` to (a) understand the entry format and (b) find the highest existing ID — the new entry's ID is that number + 1, zero-padded to four digits.
2. Re-read the recent conversation and identify the *decision* worth logging. A decision is something with future implications — a chosen approach, a constraint accepted, a library picked, a pattern ruled in or out. **Not** every change is a decision: bug fixes, refactors, and routine implementations usually are not. If you cannot identify a real decision, say so and stop — do not invent one.
3. Draft a new entry using the exact format documented at the top of `DECISIONS.md`:
   - Heading: `## NNNN — Short title (YYYY-MM-DD)` using today's date.
   - **Context.** One or two sentences on the problem or constraint.
   - **Decision.** One or two sentences on what was chosen.
   - **Consequences.** What this implies for future work; what it rules out. Be concrete.
   - **Supersedes / Superseded by.** Only if it relates to an existing entry.
4. Show the drafted entry to the user and ask for confirmation or edits **before** writing it. Do not append to `DECISIONS.md` until the user approves.
5. After approval, append the entry to `DECISIONS.md` (do not edit existing entries — append only).
6. If this decision also implies a change to `CLAUDE.md` (a new convention future agents need to follow) or warrants a new skill in `.claude/skills/`, mention that as a follow-up suggestion — do not make those changes in the same step.

Keep the entry tight. Long entries don't get read.

User arguments (optional hint about what to log): $ARGUMENTS
