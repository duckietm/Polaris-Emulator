#!/usr/bin/env bash
set -u

allow_dirty=false
if [[ "${1:-}" == "--allow-dirty" ]]; then
    allow_dirty=true
    shift
fi
search_terms="$*"

failures=0
warn() { printf 'WARN: %s\n' "$*" >&2; }
fail() { printf 'FAIL: %s\n' "$*" >&2; failures=$((failures + 1)); }

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || {
    printf 'FAIL: run inside a Polaris Git worktree\n' >&2
    exit 2
}
cd "$repo_root" || exit 2

if ! git remote get-url origin >/dev/null 2>&1; then
    printf 'FAIL: origin remote is not configured\n' >&2
    exit 2
fi

printf 'Repository: %s\n' "$repo_root"
printf 'Fetching origin...\n'
if ! git fetch origin --prune; then
    printf 'FAIL: could not refresh origin; do not implement from an unverified base\n' >&2
    exit 2
fi

if ! git show-ref --verify --quiet refs/remotes/origin/dev; then
    printf 'FAIL: origin/dev does not exist\n' >&2
    exit 2
fi

branch="$(git branch --show-current)"
head_sha="$(git rev-parse HEAD)"
dev_sha="$(git rev-parse origin/dev)"
printf 'Branch: %s\n' "${branch:-DETACHED}"
printf 'HEAD: %s\n' "$head_sha"
printf 'origin/dev: %s\n' "$dev_sha"

if [[ -z "$branch" ]]; then
    fail 'detached HEAD; create a task branch from origin/dev'
elif [[ "$branch" == "dev" || "$branch" == "main" ]]; then
    fail "do not implement directly on $branch"
fi

read -r ahead behind < <(git rev-list --left-right --count HEAD...origin/dev)
printf 'Ahead/behind origin/dev: %s/%s\n' "$ahead" "$behind"
if ! git merge-base --is-ancestor origin/dev HEAD; then
    fail 'HEAD does not contain current origin/dev; align safely before implementation'
fi

status="$(git status --porcelain=v1 --untracked-files=all)"
if [[ -n "$status" ]]; then
    printf 'Worktree changes:\n%s\n' "$status"
    if [[ "$allow_dirty" == true ]]; then
        warn 'dirty worktree allowed for this audit only'
    else
        fail 'worktree is dirty; preserve it and use a clean branch/worktree'
    fi
else
    printf 'Worktree: clean\n'
fi

java_line="$(java -version 2>&1 | head -n 1 || true)"
printf 'Java: %s\n' "${java_line:-unavailable}"
if [[ "$java_line" =~ \"25([.\"]|$) ]]; then
    printf 'Java gate: JDK 25 primary-CI parity\n'
elif [[ "$java_line" =~ \"26([.\"]|$) ]]; then
    warn 'JDK 26 is supported for packaging, but the primary full CI job still runs on JDK 25'
else
    fail 'use a JDK allowed by current Emulator/pom.xml (CI currently exercises JDK 25 and 26)'
fi

if [[ -n "$search_terms" ]]; then
    printf '\nRecent matching commits on origin/dev:\n'
    read -r -a search_words <<< "$search_terms"
    grep_arguments=()
    for word in "${search_words[@]}"; do
        grep_arguments+=(--grep="$word")
    done
    git log origin/dev -i --oneline "${grep_arguments[@]}" -n 20 || true

    if command -v gh >/dev/null 2>&1; then
        remote_url="$(git remote get-url origin)"
        repo_slug="$(printf '%s' "$remote_url" | sed -E 's#^git@[^:]+:##; s#^https?://[^/]+/##; s#\.git$##')"
        printf '\nMatching GitHub issues and pull requests:\n'
        if ! gh search issues "$search_terms" --repo "$repo_slug" --include-prs --limit 20 \
            --json number,title,state,isPullRequest,url 2>/dev/null; then
            warn 'GitHub overlap search was unavailable; search PRs/issues manually'
        fi
    else
        warn 'gh is unavailable; search GitHub PRs/issues manually'
    fi
else
    warn 'no task keywords supplied; code/history/PR overlap search remains required'
fi

if ((failures > 0)); then
    printf '\nPreflight blocked with %d failure(s).\n' "$failures" >&2
    exit 1
fi

printf '\nPreflight passed. Record origin/dev=%s in the task notes.\n' "$dev_sha"
