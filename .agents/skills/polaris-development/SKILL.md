---
name: polaris-development
description: Prepare, implement, review, or finish any Polaris Emulator feature, bugfix, refactor, migration, fixture, test, CI, packaging, or pull-request task. Use before changing Polaris code so work starts from current origin/dev, checks for overlapping work, preserves compatibility, selects the right fixtures and contracts, and reproduces the applicable CI gates before claiming merge readiness.
---

# Polaris development

Treat `origin/dev` and the checked-in repository as live evidence. Never rely on an old checkout, an earlier conversation, PR labels, or remembered CI behavior.

## Start from the live base

Before planning or editing:

1. Locate the Polaris repository root and read its tracked `AGENTS.md`, tracked `CLAUDE.md` when present, `POLARIS.md`, relevant local docs, and the active workflow files. Treat untracked guidance as local context, not project policy, until confirmed.
2. Run `scripts/preflight.sh "<task keywords>"` from this skill. Use concrete domain terms such as class names, packet names, migrations, or issue wording.
3. Report the current branch, `origin/dev` SHA, ahead/behind state, worktree state, and any overlapping code, commits, branches, issues, or PRs.
4. Stop implementation when the worktree is dirty, the branch does not contain current `origin/dev`, or the current branch is `dev`/`main`. Preserve existing work. Do not reset, clean, stash, switch, rebase, or force-push it without explicit authorization.
5. For an authorized implementation, use a clean `feature/<slug>` or `bugfix/<slug>` branch created from current `origin/dev`. If the current worktree cannot be aligned safely, create a separate Git worktree from `origin/dev` instead of disturbing it.

Do not interpret “fetch succeeded” as “aligned.” Prove alignment with `git merge-base --is-ancestor origin/dev HEAD` after the fetch. Re-run this check immediately before publication because `dev` may move during the task.

## Avoid duplicate work

Search before designing:

- Use `rg` across source, tests, migrations, protocol contracts, scripts, and docs.
- Inspect `git log origin/dev -- <relevant paths>` and remote branches containing related commits.
- When GitHub access is available, search open and merged PRs plus open issues using the task keywords.
- Inspect existing fixtures and characterization tests before creating a new test harness.
- Extend the established implementation, fixture, or contract when it owns the behavior. Create a new mechanism only when the existing one cannot express the requirement, and state why.

Summarize the overlap check before substantial implementation. A similarly named class alone is not proof of duplication; trace the actual execution path and test coverage.

## Design under Polaris constraints

- Keep fixes self-contained in Polaris, including Polaris-owned migrations. Do not require CMS, Nitro, proxy, plugin, or deployed schema consumers to change.
- Treat the public plugin surface as a frozen binary ABI. Preserve packages, signatures, classloading, resources, live collection behavior, object identity, lifecycle ordering, packet bytes, and persistence semantics where applicable.
- Favor safe, data-preserving defaults and plain-language diagnostics for hotel owners. Keep strict or advanced behavior opt-in.
- For a bugfix, first add a focused failing reproduction. For a refactor, first add characterization tests that pass on the unchanged implementation. Do not weaken a fixture, contract, baseline, or parity assertion to make new code pass.
- Make the narrowest coherent change and keep unrelated cleanup out of the branch.

Read [references/fixtures-and-contracts.md](references/fixtures-and-contracts.md) whenever the task touches persistence, migrations, plugins, packets, furni, e2e behavior, or compatibility. Read [references/ci-validation.md](references/ci-validation.md) before implementation planning and again before declaring the task complete.

## Implement and validate

1. Record a focused test or existing contract that proves the requested behavior.
2. Implement the smallest compatible change.
3. Run the focused test, then the broader gates selected from the CI reference.
4. Compare the final diff with `origin/dev`; check for generated files, stale contracts, accidental assets, secrets, logs, JARs, backups, or unrelated edits.
5. Fetch again and prove the branch still contains current `origin/dev`. If it no longer does, integrate the new base safely and rerun affected gates.
6. Report exact commands and results. Separate passed gates from gates not run, environmental limitations, and residual compatibility risk. Never call work “CI-ready,” “merge-ready,” or regression-free when a required gate was skipped or only inferred.

## Git and publication

- Use Conventional Commits.
- Keep commit and pull-request descriptions concise, lean, informative, and ADHD-friendly. Make them easy to understand at a glance.
- Do not add dedicated “Why” or “Verification” sections. Use a short overview and only a few compact bullets when they materially improve clarity.
- Mention important test status briefly without turning the description into a validation report.
- Do not push, open a PR, merge, alter remote branches, or accept ABI/baseline divergence unless the user explicitly requests that action.
