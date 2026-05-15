#!/usr/bin/env bash
set -euo pipefail

DEFAULT_BRANCH=$(gh api "repos/${GITHUB_REPOSITORY}" --jq '.default_branch')

PR_NUMBERS=$(gh pr list \
  --base "${DEFAULT_BRANCH}" \
  --state open \
  --json number,isDraft \
  --jq '.[] | select(.isDraft == false) | .number')

for PR_NUMBER in $PR_NUMBERS; do
  echo "Checking PR #${PR_NUMBER}..."

  PR_DATA=$(gh api "repos/${GITHUB_REPOSITORY}/pulls/${PR_NUMBER}")
  HEAD_SHA=$(echo "$PR_DATA" | jq -r '.head.sha')
  HEAD_REF=$(echo "$PR_DATA" | jq -r '.head.ref')
  IS_FORK=$(echo "$PR_DATA" | jq -r '.head.repo.fork')
  MERGEABLE_STATE=$(echo "$PR_DATA" | jq -r '.mergeable_state')

  CHECKS=$(gh api "repos/${GITHUB_REPOSITORY}/commits/${HEAD_SHA}/check-runs")
  RUNS_COUNT=$(echo "$CHECKS" | jq '.check_runs | length')

  if [ "$RUNS_COUNT" -eq 0 ]; then
    echo "PR #${PR_NUMBER}: no checks found, skipping"
    continue
  fi

  HAS_FAILING=$(echo "$CHECKS" | jq '[.check_runs[] | select(.status != "completed" or (.conclusion | IN("failure", "cancelled", "timed_out", "action_required")))] | length > 0')

  if [ "$HAS_FAILING" = "true" ]; then
    echo "PR #${PR_NUMBER}: checks not all passing, skipping"
    continue
  fi

  if [ "$MERGEABLE_STATE" != "clean" ]; then
    echo "PR #${PR_NUMBER}: mergeable_state is '${MERGEABLE_STATE}', not clean, skipping"
    continue
  fi

  echo "PR #${PR_NUMBER}: merging..."
  if gh api "repos/${GITHUB_REPOSITORY}/pulls/${PR_NUMBER}/merge" \
       -X PUT \
       -f merge_method=squash; then
    echo "PR #${PR_NUMBER}: merged successfully"
    if [ "$IS_FORK" = "false" ]; then
      gh api "repos/${GITHUB_REPOSITORY}/git/refs/heads/${HEAD_REF}" -X DELETE \
        && echo "Deleted branch ${HEAD_REF}" \
        || echo "Could not delete branch ${HEAD_REF}"
    fi
  else
    echo "PR #${PR_NUMBER}: could not merge"
  fi
done
