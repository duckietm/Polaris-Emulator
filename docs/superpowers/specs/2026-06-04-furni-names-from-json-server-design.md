# Furni names from JSON (server-authoritative) — Design

- **Date:** 2026-06-04
- **Status:** Draft for review
- **Scope:** Cross-repo — Arcturus (emulator), Nitro_Render_V3 (renderer), Nitro-V3 (client)
- **Out of scope:** furni-editor feature/packets, NitroV3-Housekeeping (CMS), server-side multi-language, description rendering in the infostand.

## 1. Problem & motivation

Today a furni's display name lives in **two independent places** that drift apart:

- **DB** — `items_base.public_name` (`Item.fullName`), used by the emulator.
- **furnidata JSON** — used by the client (the client already resolves all visible furni
  names/descriptions from furnidata, keyed by classname).

This forces admins to maintain names twice and causes mismatches. We want **one source of
truth**: the **furnidata JSON owns display names & descriptions**, the **DB owns technical
data**. Editing furnidata should reflect everywhere — server-pronounced strings and every
connected client — **live**, with no DB edit and no restart.

This is a single, unified refactor whose payoff is admin furni management: one place to edit,
consistent everywhere.

## 2. Source-of-truth contract

| Concern | Owner | Storage | Read by |
|---|---|---|---|
| `classname` (`item_name` / `Item.name`) | **DB** | `items_base.item_name` | join key → furnidata **and** `.nitro` asset; `isPet/isBot`; wired `wf_` fallback |
| technical data (dimensions, `stateCount`, flags, interaction, effects) | **DB** | `items_base.*` | emulator simulation |
| **display name** | **JSON** | furnidata (per classname) | emulator (`getDisplayName`) + client (furnidata, unchanged) |
| **description** | **JSON** | furnidata (per classname) | client only (catalog) — **no server consumer** |

Invariants:

1. The **bridge is `classname`**, not a numeric id. `Item.name` ↔ furnidata `classname`.
2. `public_name` (`Item.fullName`) is **NOT removed**: it remains (a) the fallback when a
   classname is missing from furnidata, and (b) the technical token for wired furni
   (`Item.java:107-116` reads `fullName.startsWith("wf_")`). No schema migration. No DROP.
3. There is **no `description` column** in `items_base`; description is JSON-only and has no
   server consumer → the emulator gets **no** `getDescription()`.
4. **One furnidata artifact** is shared truth: the file the emulator indexes must be the same
   furnidata the client loads (deploy invariant, §7).
5. Server emits names in the **base locale** of the furnidata file. Player-facing multi-language
   stays a client localization-layer concern (unchanged).

## 3. Architecture — two independent pieces

The refactor is two pieces that share only the furnidata file and one new packet. They do not
depend on each other.

- **Piece 1 — Server-authoritative names.** The emulator's pronounced names come from furnidata.
- **Piece 2 — Liveness via delta.** When the furnidata file changes, connected clients (and the
  server index) update without reconnecting, via a minimal delta broadcast.

## 4. Piece 1 — Emulator (server-authoritative names)

### 4.1 `FurnidataReader` (new, package `com.eu.habbo.habbohotel.items`)

A neutral, shared reader extracted so the editor is **not touched**. Responsibilities:

- Resolve the furnidata source reusing the **same already-configured** path as the editor:
  `furni.editor.renderer.config.path` → `furnidata.url` → `furni.editor.asset.base.path`
  (see `FurniDataManager.resolveSource()` for the exact resolution we mirror). Default to those
  values so admins configure **once**.
- Support both layouts the editor already supports: **single file** (`FurnitureData.json`) and
  **split-tier directory** (`core/custom/seasonal`, `manifest.json5`, JSON5 with comments;
  later tiers override earlier). Reuse the JSON5 strip logic (extract to the shared reader).
- Parse `roomitemtypes` (floor) and `wallitemtypes` (wall) → return a flat list of
  `FurnidataEntry { int id, String classname, FurnitureType type, String name, String description }`.

**Security requirements on the reader (furnidata is untrusted input):**

- **Path-traversal guard.** When resolving split-tier manifest entries
  (`tiers[]`, `files[]`) via `dir.resolve(name)`, normalize the result and **reject any path that
  escapes the configured base dir** (absolute paths, `..`). The existing `FurniDataManager` lacks
  this guard — the shared reader MUST add it (do not propagate the gap).
- **Size cap.** Refuse to load a furnidata file/dir above a configurable max (default e.g. 64 MB)
  to bound parse cost.
- **Sanitization at the boundary.** Every `name`/`description` is sanitized on load:
  truncate to **256 chars**, strip control characters and newlines, and **neutralize `%` tokens**
  (so they cannot inject into `String.replace` placeholder chains, server- or wired-side).
  Normal text/emoji/non-latin scripts pass through.
- **Fail-safe.** Any IO/parse error is caught and logged; the provider keeps the **last-good
  index** (or empty on first load) and never throws — boot must not crash on a bad furnidata.

### 4.2 `FurnitureTextProvider` (new, package `items`)

- Holds `volatile Map<String /*classname lowercase*/, FurniText {int id, String name, String description}>`.
- `reindex()`: read via `FurnidataReader` → build a new immutable map → compute delta vs the
  previous map (§5) → atomically swap the reference → return the delta.
- Initialized in `GameEnvironment.load` near `ItemManager`. Resolution is **lazy**, so boot order
  is not critical and `Item` objects do not depend on the provider at load time.
- Toggle `items.furnidata.names.enabled` (default `true`). When `false`, `getDisplayName()`
  returns the DB value (instant rollback, no recompile).

### 4.3 `Item.getDisplayName()`

```
String getDisplayName():
    if !enabled: return fullName
    FurniText t = FurnitureTextProvider.get(this.name /* classname, lowercased */)
    return (t != null && t.name not blank) ? t.name : this.fullName   // never null
```

No `getDescription()` on the server (no consumer).

### 4.4 Swap list (exhaustive — verified)

Replace `item.getFullName()` → `item.getDisplayName()` at exactly these 6 sites:

| Site | Context |
|---|---|
| `CatalogBuyItemAsGiftEvent.java:251` | LTD daily-total alert (gift) |
| `CatalogBuyItemAsGiftEvent.java:262` | LTD daily-item alert (gift) |
| `CatalogManager.java:1057` | LTD daily-total alert (buy) |
| `CatalogManager.java:1063` | LTD daily-item alert (buy) |
| `WiredTextPlaceholderUtil.java:282` | wired `%furni.name%` (keep existing `getName()` ultimate fallback) |
| `WatchAndEarnRewardComposer.java:21` | `appendString(...)` — sends name in a packet |

**Do NOT change** (technical, use `item_name`/classname): `PresentItemOpenedComposer:24`,
`GiftCommand:72`, `SendGift:82`, `SellItemEvent:37,45`, `CloseDiceEvent:34`, `isPet/isBot`, and the
wired `wf_` fallback in `Item.load`. The catalog offer/page serialization sends **no** display
name (`CatalogItem` serializes `catalog_name` + sprite only) — confirmed, nothing to change there.

## 5. Piece 2 — Liveness via delta

### 5.1 Server: file watcher + diff + broadcast

- A `WatchService` watches the resolved furnidata location on a **single, serialized watcher
  thread** (so reindex never races itself). For the **split-tier** layout, register the base dir
  and each tier dir. **Debounce** (~750 ms) to coalesce burst writes, plus a **minimum interval
  between broadcasts** (e.g. ≥5 s) to cap amplification.
- On settle → `FurnitureTextProvider.reindex()` → diff old vs new **by classname**:
  - **added** (new classname) and **changed** (name **or** description differs) → included.
  - **removed** classnames → **ignored** (rare; resolved on client reconnect).
- Broadcast decision (anti-DoS):
  - delta empty → no broadcast.
  - delta size ≤ **cap** (e.g. 500 entries) → broadcast `FurnitureDataReload` in **delta mode**.
  - delta size > cap (mass replace) → broadcast in **reload-hint mode** (compact signal; clients
    re-load furnidata at next opportunity) instead of a giant per-client payload.
- The broadcast is triggered **only** by the file watcher — there is **no client-initiated reload
  path**. This is a security property to preserve (clients cannot induce reindex/broadcast).

### 5.2 Wire contract — new packet `FurnitureDataReload`

- **Composer (Arcturus):** `FurnitureDataReloadComposer`, new dedicated header id (pick a free id;
  document on both sides). Two modes:
  ```
  int   mode           // 0 = delta, 1 = reload-hint
  // mode == 0 (delta):
  int   count          // bounded by the server cap; the client MUST also bound it on read
  count × {
    string type        // "S" (floor) | "I" (wall)
    int    id           // furnidata numeric id (for localization-key + FurnitureData lookup)
    string classname
    string name         // already sanitized server-side
    string description
  }
  // mode == 1 (reload-hint): no further fields (optionally an int revision for cache-busting)
  ```
- **Parser/Event (renderer):** `FurnitureDataReloadEvent` + `FurnitureDataReloadParser` reading the
  same shape. The parser **bounds `count`** (reject/clamp absurd values) and tolerates truncation
  (`bytesAvailable` pattern) so a malformed/MITM payload cannot allocate unbounded memory.
  Registered in `SessionDataManager.init()` via
  `GetCommunication().registerMessageEvent(new FurnitureDataReloadEvent(...))` (same pattern as the
  existing `FurniDataUpdatedEvent` registration, but a **distinct** handler).

### 5.3 Renderer: separate patch path (no editor reuse)

- New method, e.g. `SessionDataManager.applyFurnidataDelta(entries)` — **distinct** from the
  editor's `applyLiveFurnitureNameUpdate(...)` (`SessionDataManager.ts:84`), which we leave intact.
- **Delta mode (0):** for each entry, patch the corresponding `FurnitureData` (floor/wall, by `id`)
  — update `_localizedName` and `_description` — and re-register the localization keys
  `roomItem.name/desc.{id}` / `wallItem.name/desc.{id}` (mirrors `FurnitureDataLoader:105-110`).
- **Reload-hint mode (1):** re-run the furnidata load (`FurnitureDataLoader`, re-fetching
  `furnidata.url` with cache-bust) — the appropriate response to a mass change.
- In both modes, after the batch dispatch the window event **once**:
  `window.dispatchEvent(new CustomEvent('nitro-localization-updated'))`.

### 5.4 Client: zero changes

All three furni surfaces already subscribe to `nitro-localization-updated` and re-derive:

- catalog — `useCatalog.ts:919`
- inventory — `useInventoryFurni.ts:137` (→ `refreshGroupItemsLocalization`)
- infostand — `useAvatarInfoWidget.ts:425` (→ `getFurniInfo`, which reads `furnitureData.name`)

No Nitro-V3 edits are required for Piece 2.

## 6. Admin-facing outcome

Edit one place — the **furnidata JSON** — and display names update **live** across:
server-pronounced strings (catalog LTD alerts, wired `%furni.name%`, Watch&Earn), and every
connected client's catalog, inventory, and furni infostand. No DB edit, no restart, no double
maintenance.

## 7. Constraints, risks, invariants

1. **Locale no-clobber.** If per-locale furni text override files are in use (they override
   `roomItem.name.{id}` after furnidata load), a live delta that re-registers base names would
   revert overridden ids to base. Mitigation options for the plan: re-apply active overrides after
   the delta, or skip the localization-key patch for ids with an active override (still patch the
   `FurnitureData` object). For single-furnidata setups (typical retro) there is no override and no
   issue. **Document the limitation.**
2. **Deploy invariant.** `furni.editor.asset.base.path`/`furnidata.url` (what the emulator watches)
   and the furnidata the client loaded must be the **same artifact**, else the server delta
   references entries the client doesn't have.
3. **`public_name` fallback.** Wired `wf_` items absent from furnidata would show the raw `wf_…`
   token as their display name (internal/invisible furni — acceptable).
4. **Split-layout watcher.** The watcher must register all tier dirs; missing a tier dir means live
   updates from that tier are not detected (resolved on reconnect).
5. **Performance.** `getDisplayName()` is a single `HashMap` lookup on cold paths (catalog alerts,
   wired text, Watch&Earn) — negligible.

## 8. Security

With this refactor the **furnidata becomes a security-relevant input**: its strings now flow into
server output (catalog LTD alerts, wired `%furni.name%`, the Watch&Earn packet) and into a
broadcast to every connected client. Regular players cannot influence names (names are admin-owned,
keyed by classname); the threat is **untrusted furnidata content** (third-party furni packs,
imports, a compromised editor/supply chain). Controls:

1. **Boundary sanitization** (see §4.1): cap 256 chars, strip control/newline, **neutralize `%`**.
   Neutralizing `%` at load makes every `String.replace("%itemname%", name)` /
   `%furni.name%` site injection-safe; as defense-in-depth, substitute the (untrusted) furni name
   **last** in any placeholder chain.
2. **Path-traversal guard** in the shared reader (§4.1) — reject manifest paths escaping the base
   dir. Closes a gap the current editor reader does not cover.
3. **DoS / amplification controls** (§5.1): single serialized watcher thread, debounce + minimum
   broadcast interval, delta-size cap with **reload-hint fallback** for mass changes, furnidata
   file-size cap.
4. **Fail-safe loading** (§4.1): bad/corrupt furnidata never crashes boot; last-good index is kept;
   `getDisplayName()` falls back to `public_name`.
5. **Robust client parser** (§5.2): bound `count`, tolerate truncation — a malformed/MITM
   `FurnitureDataReload` cannot allocate unbounded memory client-side.
6. **No client-triggered reload** (§5.1): only the file watcher broadcasts. Do not add any
   client→server reload request. Preserve this property.
7. **Minimal disclosure**: the delta carries **only** `name`/`description` (already public via
   furnidata) — never other fields from the server-side file.
8. **Concurrency**: `volatile` index reference + atomic swap + single reindex thread → no torn reads.

## 9. Testing

- **Emulator (JUnit):** `FurnidataReader` parses single-file and split-tier (JSON5, tier override);
  `FurnitureTextProvider` lookup by lowercased classname, **fallback to `public_name`** when absent,
  atomic reindex; `reindex()` diff produces correct added/changed delta and ignores removals;
  `Item.getDisplayName()` honors the enable toggle.
- **Renderer (Vitest):** `FurnitureDataReloadParser` reads the payload shape; `applyFurnidataDelta`
  patches floor/wall `FurnitureData` by id, re-registers localization keys, dispatches
  `nitro-localization-updated` once.
- **Client (Vitest):** existing subscribers (`useCatalog`, `useInventoryFurni`, `useAvatarInfoWidget`)
  refresh on `nitro-localization-updated` (regression guard; no new code).
- **Manual acceptance:** edit a furni name in furnidata → live update in catalog + inventory +
  infostand without refresh; a wired `%furni.name%` sign and a Watch&Earn reward show the new name.
- **Security tests:** reader rejects a split-tier manifest with `../` traversal; a name containing
  `%limit%`/`%user.name%` does not inject into catalog alerts or wired text (`%` neutralized);
  oversized furnidata is refused; corrupt furnidata keeps last-good index and does not crash;
  a mass change emits a reload-hint (not a giant delta); the client parser clamps an absurd `count`.

## 10. Open questions

- Free header id for `FurnitureDataReload` (assign during implementation; document both sides).
- Whether any retro on this stack actually ships per-locale furni override files (governs whether
  constraint §7.1 is live or moot).
