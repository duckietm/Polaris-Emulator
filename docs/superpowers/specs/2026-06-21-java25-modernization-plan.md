# Java 25 idiom modernization — plan & progress

**Date:** 2026-06-21 · derived from an 8-agent survey (76 opportunities) + synthesis.
Execute incrementally: **one commit per batch** (per-package/per-class within big
batches), `mvn test` (406+) green as the gate after each, revert the single commit
if red. The pom prerequisite (release=25) is already done.

## Batches (ordered by safety/value)

| # | Batch | Sites | Status |
|---|---|---|---|
| 1 | `Math.clamp(v,min,max)` replaces nested `min(max(...))` | 5 (4 safe) | ✅ done (`7f1cef10`) — skipped SetStackHelperHeight (lo>hi risk) |
| 10a | Virtual-thread executor: YouTube playlist HTTP loader | 1 | ✅ done (`7f1cef10`) |
| 6 | Switch expressions (value-mapper / return-only) | ~30 | ✅ done — FurnitureType (`2e4176cd`) + SettingsState + wave 5 (`6b12b1e4`, 10 switches). Skipped CatalogFeaturedPage (heterogeneous append* calls) |
| 2 | `Collectors.toList()/toSet()` → `Stream.toList()/toUnmodifiableSet()` | ~50 | ✅ done — wave 6 (`0d44e4d3`), 46 files. Two-stage **adversarial** workflow: stage-2 skeptic traced every result into callers/ctors/Gson; zero unsafe, escaping/unverifiable sites skipped |
| 3 | Pattern matching for `instanceof` (test-then-cast) | ~445 | ✅ done — waves 1-4 (`f9e59fdb`, `611b7985`, `0be2381e` + items/interactions). ~340 conversions across the whole module; non-candidates (bool checks, `==`, OR-chains, cast-to-other-type) correctly skipped |
| 4 | Pattern matching `instanceof` guarded-`&&` + negated early-exit | ~42 | ✅ done — folded into waves 1-4; compiler-verified definite-assignment for every binding |
| 11 | `getFirst()/getLast()/removeLast()` (sequenced collections) | 56 surveyed | ✅ done — wave 7 (`bfa8edfd`), 12 guarded+typed sites. Unguarded / JsonArray / trove sites left as-is |
| 7 | Fall-through / multi-label switch collapsing (`case A, B ->`) | ~8 | ◑ partial — the value-mapper ones folded into wave 5 (e.g. CatalogPageType `case A, B ->`). Remaining pure-statement fall-throughs are low-value; deferred |
| 5 | **Seal** the 3 Wired base hierarchies (Condition/Effect/Trigger) | 112 subclasses | ⏸ **DEFERRED — needs human greenlight** (see below) |
| 8 | Records for vetted immutable carriers | 12 classes | ⏸ **DEFERRED — needs human greenlight** (see below) |
| 9 | `ScopedValue` for the wired request-scoped `ThreadLocal`s | 11 (not 5) | ⏸ **DEFERRED — needs human greenlight** (see below) |

## Deferred structural batches — why, and what greenlight they need

All mechanical/idiom batches above are **done and green (406 tests)**. The three
remaining batches are *structural* refactors. Under the standing "correctness is
paramount" constraint, and given the unit suite only weakly exercises the wired
runtime, these were intentionally **not** applied autonomously — each carries a
real regression surface that the compiler and the current tests would not catch.
They are vetted and ready; they need an explicit human decision.

### 8 — Records  (LOW value / MEDIUM friction)
The 12 candidates (FriendRequest, GuideChatMessage, CryptoConfig,
UserCustomizationData, VoucherHistoryEntry, WiredHighscoreDataEntry,
WiredHighscoreRow [keeps Comparable], HabboMention, EarningsReward, EarningsEntry,
EarningsClaimResult, CatalogPurchaseLogEntry) are genuinely immutable carriers,
**but** converting to a record changes the public API: `getX()` getters become
component accessors `x()`, and public fields become private-final. Every caller
that does `obj.getX()` / `obj.field` must change. So each conversion is a class +
all-callers change, for mostly cosmetic gain. Recommend: convert ONLY the few with
no external getters/public-field callers; keep a secondary canonical/ResultSet
constructor where the DB layer builds them. Do one-per-commit, caller sweep each.

### 5 — Seal the Wired hierarchies  (LOW realized value / HIGH risk)
Sealing `InteractionWiredCondition/Effect/Trigger` requires a modifier
(`final`/`sealed`/`non-sealed`) on **all 112 subclasses**, and a `permits` clause.
Two blockers: (a) the payoff (exhaustive `switch` over wired types) is **not used**
— dispatch is via `instanceof`/registration, so sealing buys compiler-verified
exhaustiveness we don't consume; (b) the emulator has a **PluginManager** — third-
party plugins may extend these interactions, and `final`/`sealed` would break them
at the source/binary level. Recommend: do NOT seal unless the plugin-extension
contract is confirmed closed. Otherwise high churn + ecosystem-break risk for
little gain.

### 9 — ScopedValue for wired ThreadLocals  (MEDIUM value / MEDIUM-HIGH risk)
Survey found **11** request-scoped `ThreadLocal`s (not 5): 4 GOOD-FIT, 6
MEDIUM-FIT, 0 BAD-FIT.
- GOOD-FIT (clean bounded set→use→remove, already wrapped in try-finally /
  AutoCloseable): `WiredUserMovementHelper.SUPPRESSED_STATUS_ROOM_UNIT_IDS`,
  `WiredMoveCarryHelper.SUPPRESSED_STATUS_ROOM_UNIT_IDS`,
  `WiredSelectionFilterSupport.FILTER_DEPTH`,
  `WiredInternalVariableSupport.USER_MOVE_INSTANT_OVERRIDE`.
- MEDIUM-FIT (depth-counters / begin…finish public-API spans / recursive event
  handling): `WiredMoveCarryHelper.COLLECTED_MOVEMENTS` + `MOVEMENT_COLLECTION_DEPTH`,
  `WiredManager.EVENT_HANDLING_DEPTH` + `DEFERRED_EFFECT_EVENTS`,
  `WiredInternalVariableSupport.USER_MOVE_BATCH` + `USER_MOVE_BATCH_DEPTH`.

Why deferred: the existing ThreadLocals already work **correctly** with their
try-finally/AutoCloseable scoping; `ScopedValue` is immutable per binding, so the
mutable depth-counters are not a clean fit, and migrating restructures control flow
in the most complex, least-unit-tested subsystem (wired). The virtual-thread
memory benefit is marginal here (these run on game-logic threads, vthreads were
opt-in/limited). Recommend: if pursued, start with the 4 GOOD-FIT as a proving
ground, behind a runtime smoke test of wired movement/effects — not blind.

## Records — the 12 vetted candidates (batch 8, detail)
FriendRequest, GuideChatMessage, CryptoConfig, UserCustomizationData,
VoucherHistoryEntry, WiredHighscoreDataEntry, WiredHighscoreRow (keeps Comparable),
HabboMention, EarningsReward, EarningsEntry, EarningsClaimResult,
CatalogPurchaseLogEntry. Verify per-class: no identity-equality reliance, no field
mutation, before converting.

## Explicitly NOT mechanical (each gets its own reviewed change)
- Text blocks for SQL (whitespace changes the string — verify per-site).
- Virtual threads for `AuthHttpHandler.AUTH_EXECUTOR` (deliberate bound = backpressure;
  BCrypt is CPU-bound) and `WiredTickService`/`ThreadPooling` (ordering/identity).
- `instanceof` chains → exhaustive switch patterns (do AFTER sealing).
- Mass `var` / enhanced-for / `String.format` rewrites (low value, high churn).
- `EmulatorDashboard` metrics scheduler (it's a *scheduled* executor — no vthread drop-in).

## Method
Mechanical batches can be fanned out per-file (agents edit distinct files, no
conflict), then verified by one `mvn test`; compile errors name the offending file.
But behaviour-sensitive items (records identity, switch fall-through, vthread bounds)
are done by hand with per-site judgment — verify before applying (this already caught
a false-positive ResultSet "leak" and two unsafe vthread/clamp sites).
