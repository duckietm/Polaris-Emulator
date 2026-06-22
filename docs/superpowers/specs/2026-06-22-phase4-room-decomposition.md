# Phase 4 — Architecture (god-class decomposition), with care

**Date:** 2026-06-22 · scope: non-wired. Gate: `mvn test` green (414) on JDK 25.
Approach: behavior-preserving "extract/consolidate", keeping `Room`'s public API
as delegating wrappers so **no external caller changes** — only `Room` and the
target manager change. The unit suite does not exercise room runtime, so each
step is kept mechanically minimal and reasoned from the code.

## Survey finding — `Room` decomposition is littered with half-finished extracts

`Room` (2930 lines, 15 already-extracted managers) still holds state that was
*partially* moved to a manager, leaving duplicated/stranded copies:
- `mutedHabbos` — also in `RoomChatManager`
- `userVotes`/`noVotes`/`yesVotes` — also in `RoomWordQuizManager` (distinct
  feature: room-rating vs word-quiz poll; the `Room` ones are public fields read
  by `RoomManager`, so not a safe blind extract)
- `bannedHabbos` — also in `RoomRightsManager` → **this one was an active bug**

## Step 1 — consolidate room bans (also fixes a latent correctness bug)

**The bug.** Ban handling was extracted to `RoomRightsManager` (it owns
`isBanned()`, `unbanHabbo()`, `addRoomBan()`, and a `loadBans()`), but `Room` kept
its own parallel `bannedHabbos` map:
- `Room`'s constructor eagerly called **`Room.loadBans`** (comment: *"needed for
  entry check before loadData"*), filling **`Room.bannedHabbos`**.
- `RoomRightsManager.loadBans` was **never called** (zero callers), so the rights
  manager's map started **empty** and only received runtime `addRoomBan` entries.
- `Room.isBanned()` delegates to `rightsManager.isBanned()`, which reads the
  **empty** map → **DB-persisted bans were not enforced** at room entry.
- `Room.getBannedHabbos()` returned `Room`'s map (used by
  `RoomBannedUsersComposer`) → the banned-users list ignored runtime ban/unban,
  i.e. **stale**.

Both `loadBans` methods were byte-for-byte the same query on `room_bans`.

**The fix.** Single authoritative ban map in `RoomRightsManager`:
1. `Room`'s constructor now calls `this.rightsManager.loadBans(connection)` after
   `initializeManagers()` (still eager, still before `loadData()`; the rights
   manager only exists post-`initializeManagers`).
2. Removed `Room.bannedHabbos` (field + init) and the dead `Room.loadBans`.
3. `Room.getBannedHabbos()` now delegates to `rightsManager.getBannedHabbos()`.

`Room`'s public API (`getBannedHabbos/isBanned/unbanHabbo/addRoomBan`) is
unchanged → no external caller changes. Now enforcement, the displayed list, and
runtime add/remove all use the one loaded map: DB bans are enforced again and the
list reflects live changes.

**Caveat.** The 414-test suite doesn't exercise the ban runtime, so this is
verified by code-reading + green build, not integration. Recommend a quick live
check (ban a user, restart, confirm they can't re-enter; ban/unban and confirm
the list updates) when next on a DB-backed instance.

## Step 2 — remove `Room`'s dead `mutedHabbos` map

Mute handling was extracted to `RoomChatManager` (`Room.muteHabbo/unmuteHabbo/
isMuted(Habbo)` all delegate to it; the live callers are `RoomUserMuteEvent`,
wired mute effects, etc.). But `Room` kept a `mutedHabbos` field that was **never
populated and never queried** — its only use was `this.mutedHabbos.clear()` in
`Room.dispose()`, a no-op on a permanently empty map (and not exposed via any
getter).

Removed the field, its initializer, the now-unused `TIntIntHashMap` import, and
the no-op clear in `dispose()`. Behavior-neutral (clearing an always-empty map
observes nothing); pure god-class state reduction. `mvn test` green.

(Note: the dispose-time clear's *intent* was "clear mutes on unload", but the
live map lives in `RoomChatManager`. Mutes are in-memory and self-expiring by
timestamp, and a disposed room is replaced by a fresh `Room`+`RoomChatManager` on
reload, so not clearing the live map on dispose is not observable — redirecting
the clear would also be a behavior change, so it was left out.)

## Step 3 — remove `RoomRightsManager`'s dead mute cluster

`RoomRightsManager` carried a full, **zero-caller** mute cluster — `mutedHabbos`
field + `muteHabbo`/`isMuted`/`getMuteEndTime`/`getMutedHabbos`/`clearMutes` — a
second dead duplicate of the `RoomChatManager` logic. Verified zero callers for
every method (the live path is `Room.muteHabbo/isMuted` → `RoomChatManager`).
Removed the field, its initializer, all five methods (keeping the unrelated
`getRights()` that sat among them), and the now-unused `TIntIntHashMap` import.
`getMutedHabbos()` had no callers anywhere, so dropping these public methods is
safe for the emulator (only a theoretical plugin that reached into
`getRightsManager().muteHabbo(...)` would notice — and the real API has always
been `Room.muteHabbo(...)`).

**Result:** mute state now lives in exactly one place, `RoomChatManager`.

## Verification
- `mvn test` green: 414 tests, 0 failures, JDK 25 (all steps).
