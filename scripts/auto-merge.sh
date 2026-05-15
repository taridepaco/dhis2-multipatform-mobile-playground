#!/usr/bin/env bash
set -euo pipefail

DEFAULT_BRANCH=$(gh api "repos/${GITHUB_REPOSITORY}" --jq '.default_branch')

# Determine which PR(s) to process.
# For event-driven runs, PR_NUMBER is set to the PR that triggered the workflow.
# For workflow_dispatch (manual), there is no triggering PR so we process all open ones.
if [ "${GITHUB_EVENT_NAME:-}" = "workflow_dispatch" ]; then
  echo "Manual trigger: processing all open non-draft PRs"
  PR_NUMBERS=$(gh pr list \
    --base "${DEFAULT_BRANCH}" \
    --state open \
    --json number,isDraft \
    --jq '.[] | select(.isDraft == false) | .number')
elif [ -n "${PR_NUMBER:-}" ]; then
  echo "Event trigger: processing PR #${PR_NUMBER}"
  PR_NUMBERS="$PR_NUMBER"
else
  echo "Event not associated with a PR (e.g. push to branch), skipping"
  exit 0
fi

for pr in $PR_NUMBERS; do
  echo "Checking PR #${pr}..."

  PR_DATA=$(gh api "repos/${GITHUB_REPOSITORY}/pulls/${pr}")
  HEAD_SHA=$(echo "$PR_DATA" | jq -r '.head.sha')
  HEAD_REF=$(echo "$PR_DATA" | jq -r '.head.ref')
  IS_FORK=$(echo "$PR_DATA" | jq -r '.head.repo.fork')
  MERGEABLE_STATE=$(echo "$PR_DATA" | jq -r '.mergeable_state')

  CHECKS=$(gh api "repos/${GITHUB_REPOSITORY}/commits/${HEAD_SHA}/check-runs")
  RUNS_COUNT=$(echo "$CHECKS" | jq '.check_runs | length')

  if [ "$RUNS_COUNT" -eq 0 ]; then
    echo "PR #${pr}: no checks found, skipping"
    continue
  fi

  HAS_FAILING=$(echo "$CHECKS" | jq '[.check_runs[] | select(.status != "completed" or (.conclusion | IN("failure", "cancelled", "timed_out", "action_required")))] | length > 0')

  if [ "$HAS_FAILING" = "true" ]; then
    echo "PR #${pr}: checks not all passing, skipping"
    echo "  Failing / incomplete check runs:"
    echo "$CHECKS" | jq -r '
      .check_runs[]
      | select(.status != "completed" or (.conclusion | IN("failure", "cancelled", "timed_out", "action_required")))
      | "    - \(.name): status=\(.status) conclusion=\(.conclusion // "pending")"
    '
    continue
  fi

  if [ "$MERGEABLE_STATE" != "clean" ]; then
    echo "PR #${pr}: mergeable_state is '${MERGEABLE_STATE}', not clean, skipping"
    continue
  fi

  echo "PR #${pr}: merging..."
  if gh api "repos/${GITHUB_REPOSITORY}/pulls/${pr}/merge" \
       -X PUT \
       -f merge_method=squash; then
    echo "PR #${pr}: merged successfully"
    if [ "$IS_FORK" = "false" ]; then
      gh api "repos/${GITHUB_REPOSITORY}/git/refs/heads/${HEAD_REF}" -X DELETE \
        && echo "Deleted branch ${HEAD_REF}" \
        || echo "Could not delete branch ${HEAD_REF}"
    fi
  else
    echo "PR #${pr}: could not merge"
  fi
done
