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
| 6 | Switch expressions (value-mapper / return-only) | ~30 | ◑ started — FurnitureType (`2e4176cd`); ~29 left (GuildManager.rankQuery, SettingsState, EarningsCenterManager string switches, …) |
| 2 | `Collectors.toList()/toSet()` → `Stream.toList()/toSet()` | 72 + 4 | ☐ todo — ONLY where result is read-only (toList() is unmodifiable) |
| 3 | Pattern matching for `instanceof` (test-then-cast) | ~445 | ☐ todo — package by package; behaviour-preserving by construction |
| 4 | Pattern matching `instanceof` guarded-`&&` form | ~42 | ☐ todo — follows batch 3 |
| 7 | Fall-through / multi-label switch collapsing (`case A, B ->`) | ~8 | ☐ todo |
| 5 | **Seal** the 3 Wired base hierarchies (Condition/Effect/Trigger) | 112 subclasses | ☐ todo — keystone; declarative + compiler-verified |
| 8 | Records for vetted immutable carriers | 12 classes | ☐ todo — ONE per commit; keep ResultSet ctor as secondary |
| 9 | `ScopedValue` for the 5 wired request-scoped `ThreadLocal`s | 5 | ☐ todo — after sealing |
| 11 | `getFirst()/getLast()` (sequenced collections) | 56 | ☐ todo — semantics differ on EMPTY; only behind non-empty guards |

## Records — the 12 vetted candidates (batch 8)
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
