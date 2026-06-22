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

## Round 2 — ItemManager: O(1) interaction lookup instead of a per-item scan

`loadItems()` builds one `Item` per `items_base` row (~80k on the baseline DB).
Each `Item` resolves its interaction by calling
`ItemManager.getItemInteraction(String)`, which **linearly scanned**
`interactionsList` — a `THashSet` of ~200 `ItemInteraction`s — doing
`equalsIgnoreCase` until a match. That is ~80k × ~200 ≈ **16M case-insensitive
string compares** on the boot critical path, which is the bulk of the 1.65s.

**Observation.** `interactionsList` is populated only in `loadItemInteractions()`
(no public add API; `ItemInteraction` has identity equality and no external
registrant), so it is effectively immutable after load — ideal to index once.

**Change.** Added two volatile lookup maps (`name → ItemInteraction`,
`Class → ItemInteraction`) built by `rebuildInteractionIndex()`, triggered lazily
by `ensureInteractionIndex()` only when `interactionsList.size()` differs from the
last indexed size (so: built once during `loadItems()`, then a single
volatile-int compare per lookup; self-healing if the list ever changes).
`getItemInteraction(String)` / `getItemInteraction(Class)` now do an O(1) map
lookup. Boot-time interaction resolution goes from O(items × interactions) to
O(items).

**Behaviour preserved**
- `name` keys are `toLowerCase(Locale.ROOT)` and looked up the same way —
  equivalent to the old `equalsIgnoreCase` for the ASCII interaction-type tokens.
- `putIfAbsent` keeps "first registered wins", matching the old first-match scan;
  for the duplicate *classes* in the list the old scan order was already
  nondeterministic (`THashSet`), so the result set is unchanged in practice.
- Miss → default interaction (whose name is `"default"`), so `Item`'s
  `"default".equals(interactionType.getName())` `wf_` fallback path is unchanged.
- `synchronized` rebuild + volatile publication keep concurrent runtime lookups
  safe; in steady state no rebuild and no allocation occur.

`CatalogManager` (0.60s) was reviewed (`loadFurnitureValues` and the load
queries) and shows no equivalent algorithmic hot spot — it is DB-fetch + object
construction. Left for a DB-backed profiling pass rather than changed blind.

## Verification
- `mvn test` green: 414 tests, 0 failures, JDK 25 (both rounds).
