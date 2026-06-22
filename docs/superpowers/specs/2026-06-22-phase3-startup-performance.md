# Phase 3 — Startup performance

**Date:** 2026-06-22 · scope: non-wired. Gate: `mvn test` green (414) on JDK 25.
Baseline (from `2026-06-21-total-upgrade-design.md`, Temurin 25.0.3, `amx_test`):
startup ~8.07s; hot spots `FurnitureTextProvider ~2.0s` (43,031 names),
`ItemManager ~1.65s`, `CatalogManager ~0.60s`. Target = **boot latency, not RAM**.

## Round 1 — take the furnidata names parse off the critical path

`GameEnvironment.load()` ran fully sequentially:
`itemManager.load()` (1.65s) → `furnitureTextProvider.init()` (2.0s) → … all on
the boot thread.

**Key observation — no startup dependents.** The furnidata names index is read
only:
- lazily by `Item.getDisplayName()` — which is **non-memoizing** and already
  falls back to the DB `public_name` when the index has no entry (or isn't ready
  yet), so a read during the build window degrades gracefully, never wrongly
  caches, never crashes; and
- by the runtime `FurniEditor*` events (client-triggered, post-boot).

Nothing between line 85 and the end of `load()` calls it (verified by grepping
`getFurnitureTextProvider()`), and the parse itself reads files off disk —
independent of the DB-backed managers and their shared state.

**Change.** Construct `FurnitureTextProvider` early, run `init()` on a dedicated
daemon thread, and `join()` it just before `load()` announces "Loaded!". The
~2.0s parse now overlaps `itemManager.load()` and every manager constructed
after it (>2.0s of independent work), so it leaves the startup critical path
almost entirely. The `join()` guarantees the index is ready before the
environment is announced and before the game socket accepts clients — preserving
the previous invariant (names available at first use), only now built in
parallel.

**Why it's safe**
- No shared mutable state between `init()` (own index + config reads) and the
  concurrent `itemManager.load()` (DB → its own maps).
- The index is published behind a `volatile` reference and swapped atomically;
  `getName()` returns `null` → `public_name` fallback if read mid-build.
- `init()` never throws (internal try/catch) and does bounded file I/O, so the
  `join()` can't hang on it.
- Field assignment → `Thread.start()` → `join()` → socket accept gives a clean
  happens-before chain to any later client thread.

Expected effect: removes most of the 2.0s from wall-clock boot (exact number
needs a DB-backed run to confirm; not measurable in the unit sandbox).

## Not done this round (need DB-backed profiling)
`ItemManager` (1.65s, ~80k `items_base` rows + per-row interaction-class
reflection) and `CatalogManager` (0.60s) are the next targets but require a real
DB to profile/verify safely — deferred to a measured follow-up rather than
changed blind.

## Verification
- `mvn test` green: 414 tests, 0 failures, JDK 25.
