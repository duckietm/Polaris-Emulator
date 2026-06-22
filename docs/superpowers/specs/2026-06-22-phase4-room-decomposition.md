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

## Step 4 — consolidate `games` + fix games never disposed on room unload

Same shape as bans/mutes. Game handling was extracted to `RoomGameManager`
(`Room.addGame/deleteGame/getGame/getGameOrCreate/getGames` all delegate to it),
but `Room` kept its own `Set<Game> games` that — since `addGame` delegates — was
**never populated**.

**The latent bug.** `Room.dispose()` did `for (Game game : this.games)
game.dispose(); this.games.clear();` over `Room`'s **empty** set, and
`gameManager.dispose()` was **never called anywhere**. So active games were never
torn down on room unload — and `Game.dispose()` has real side effects
(`team.clearMembers()`, `teams.clear()`, `stop()`), so this orphaned game
state/timers on every unload of a room with a running game.

**The fix.** Replaced the no-op loop with `this.gameManager.dispose()` (which does
exactly the intended per-game `dispose()` + clear, on the live set), and removed
`Room`'s dead `games` field + initializer. `RoomGameManager.dispose()` had zero
prior callers, so there is no double-dispose risk. Game state now lives only in
`RoomGameManager`, and unload actually stops/cleans up running games.

**Caveat.** Behavior change in the unload path (games now stopped on unload),
verified by code-reading + green build; the suite doesn't exercise the game
runtime. Worth a live check (start a game, unload the room, confirm no orphaned
game timers) on a DB-backed instance.

## Step 5 — extract moodlight into a new `RoomMoodlightManager`

Unlike steps 1–4 (consolidating into an existing manager / removing dead state),
this is a genuine **extract-class**: there was no moodlight manager and no
duplicate. `Room` held the static `defaultMoodData` presets, the
`moodlightData` map, its parse from the `moodlight_data` DB column (constructor),
its serialization back (in `save()`, including the id 1..N renumbering), and the
`getMoodlightData()` getter.

Moved all of it **verbatim** into a new `RoomMoodlightManager`
(`new RoomMoodlightManager(columnString)` parses; `serialize()` rebuilds the
column string with the same id renumbering; `getMoodlightData()` returns the
map). `Room` now constructs the manager from `set.getString("moodlight_data")`,
`save()` writes `this.moodlightManager.serialize()`, and `getMoodlightData()`
delegates. Because the logic is moved byte-for-byte (same default presets, same
parse, same serialization with the `data.setId(id)` side effect), behaviour —
including the exact `moodlight_data` column format — is preserved.

`Room`'s public API is unchanged → external moodlight callers
(`MoodLight*Event`, `MoodLightDataComposer`) are untouched. This is the first
step that touches the `save()` SQL path, so the serialization was moved
identically rather than rewritten. Pure god-class reduction (~25 lines + a static
block out of `Room`).

## Step 6 — consolidate word-quiz state + fix the poll-display bug

`Room` carried public duplicate word-quiz fields (`wordQuiz`, `noVotes`,
`yesVotes`, `wordQuizEnd`, and `userVotes`) alongside `RoomWordQuizManager`,
which owns the live logic (`Room.handleWordQuiz/startWordQuiz/hasActiveWordQuiz/
hasVotedInWordQuiz` all delegate to it; the live writers are `AnswerPollEvent`
and `WordQuizCommand`). `Room`'s copies were only ever reset to ""/0, never given
real values.

**The bug.** `RoomManager`'s room-entry path showed the poll like this: the
*guard* used the live manager (`room.hasActiveWordQuiz()` /
`hasVotedInWordQuiz()`), but the *data* sent to the composers read `Room`'s
**dead** fields — `room.wordQuizEnd` (0), `room.wordQuiz` (""), `room.noVotes`/
`yesVotes` (0). So a user entering a room with an active word quiz saw an empty
question, a bogus elapsed time (`now - 0`), and 0/0 vote counts.

**The fix.** `RoomManager` now reads from `room.getWordQuizManager()`
(`getWordQuiz()/getWordQuizEnd()/getNoVotes()/getYesVotes()`), the live source.
Removed `Room`'s dead `wordQuiz/noVotes/yesVotes/wordQuizEnd` fields and the
no-op resets in `dispose()`.

Also removed `Room.userVotes` (`public final List<Integer>`): room *rating*
votes are tracked via `habbo.getHabboStats().votedRooms` (see
`RoomManager.hasVotedForRoom`), so this field was dead with zero readers.

This is a higher-risk step than 1–5 because it changes an **external caller**
(`RoomManager`, field-read → manager getter), accepted as part of the B-tier
work. Word-quiz state now lives only in `RoomWordQuizManager`.

## Step 7 — consolidate room rights + fix DB rights not granted on entry (permission-critical)

The fourth and worst instance of the split-state pattern. `Room` and
`RoomRightsManager` each had their own `rights` `TIntArrayList`. The runtime
mutators kept both in sync (`Room.giveRights/removeRights/removeAllRights` updated
both lists), so that part was merely redundant — **but the load was not**:

- `Room`'s async `loadData` called **`Room.loadRights`**, filling **`Room.rights`**.
- `RoomRightsManager.loadRights` was **never called** (zero callers), so the
  manager's list started **empty**.
- `hasRights()` (and thus `refreshRightsForHabbo()`, run on room entry from
  `RoomManager`) checks **`rightsManager.rights`** — empty after load. On entry a
  habbo's cached `rightsLevel` is `NONE`, so the fallback clause doesn't help.

**Result:** users with explicit room rights persisted in `room_rights` were **not
granted flat controls on entry** — they only worked if re-granted live in-session
(which writes both lists). Same family as the bans bug, but on room control
rights.

**The fix.** Single authoritative rights list in `RoomRightsManager`:
1. `loadData` now calls `this.rightsManager.loadRights(...)` instead of
   `Room.loadRights(...)`.
2. Removed `Room.rights` (field + init), the dead `Room.loadRights`, and the
   now-redundant `this.rights` updates in `giveRights/removeRights/removeAllRights`
   (the manager already maintains the list + DB).
3. `Room.getRights()` and `hasExplicitRights()` now read
   `rightsManager.getRights()`.

`Room`'s public API is unchanged (`getRights/hasRights/hasExplicitRights/
giveRights/removeRights/removeAllRights/getUsersWithRights`), so external callers
(`RoomRemoveAllRightsEvent`, etc.) are untouched. Rights state now lives only in
`RoomRightsManager`, loaded once, consulted everywhere.

**Caveat.** Permission-critical and not exercised by the unit suite; verified by
code-reading + green build, applied with explicit go-ahead. Needs a live check
(persist a right in `room_rights`, restart, confirm the user gets flat controls
on entry; then give/remove rights in-session and confirm both still work).

## Step 8 — remove `Room`'s dead `cache` field

`Room` had `public final THashMap<String, Object> cache` allocated as
`new THashMap<>(1000)` in the constructor and **never read or written** anywhere
(the `*.cache` references in the codebase are all `HabboStats.cache`, a different
field; the one other `cache` mention in `Room` is a comment about wired engine
caches). So every room allocated an unused 1000-capacity map — small per room but
multiplied across every loaded room.

Removed the field, its initializer, and the now-unused `THashMap` import.
Behaviour-neutral. (It was `public`, so a plugin could in theory have used it as
scratch space — same low plugin-compat consideration as the dead mute cluster;
it is undocumented and unused by the emulator.)

After this, the collection state still in `Room` is either properly delegated to
a manager (habbos/bots/pets/items/queue → unit/item managers) or legitimately
`Room`'s own (config scalars, the scheduling queues). The stranded-duplicate seam
that produced steps 1–8 is exhausted; further decomposition would be genuine
large extractions (room-settings + their save() SQL) or the `Emulator` god-class.

## Step 9 — consolidate room promotion + fix the broken "buy promotion" feature

The worst tangle: promotion state was split **three ways** — `Room.promoted`
(boolean, from the `rooms.promoted` column), `Room.promotion` (object, loaded by
an inline async loader in `Room`), and `RoomPromotionManager` (its own
`promotion` + `promoted`, whose `loadPromotion` had **zero callers**). Routing was
inconsistent: `getPromotion()`/`getPromotionDesc()` read `Room.promotion`, but
`isPromoted()` and `createPromotion()` used the manager.

**Confirmed user-facing bugs** (traced through `BuyRoomPromotionEvent`):
- Buying a promotion: `isPromoted()` (manager, empty) → `createPromotion()` (sets
  *manager*.promotion); then the success branch calls `room.getPromotion()`
  (reads *Room*.promotion = **null**) → `new RoomPromotionMessageComposer(room,
  null)` → NPE / broken. A **paid** feature.
- After a restart, a room with an active DB promotion reports `isPromoted()=false`
  (manager never loaded) → can't be extended, always re-created.
- **Packet desync:** `RoomDataComposer` set the promotion bit from `isPromoted()`
  (manager) but appended the promotion fields from `this.promoted` (column) —
  two different conditions, so the client could read 3 fields the server didn't
  send (or vice-versa).

**The fix.** Single source = `RoomPromotionManager`:
- `Room`'s load now calls `promotionManager.loadPromotion(this.promoted, conn)`
  (a verbatim duplicate of the old inline loader) and mirrors
  `this.promoted = promotionManager.isPromoted()` for the save column.
- `getPromotion()`/`getPromotionDesc()` delegate to the manager; removed
  `Room.promotion`.
- `RoomDataComposer` now uses one consistent `showPromotion = isPromoted() &&
  promotion != null` for **both** the bit and the appended fields (fixes the
  desync, and the empty-fields case).

`Room`'s public API is unchanged. Promotion state now lives only in
`RoomPromotionManager`, loaded once and consulted everywhere.

**Caveat.** Touches the room-info serialization and the async load; verified by
code-reading + green build, applied with explicit go-ahead, not integration-
tested. Live check: buy a promotion (no NPE, message shows), restart with an
active promo (room still promoted, extend works).

## Step 10 — characterization test for the moodlight extraction

The extractions/fixes above aren't exercised by the unit suite (they need a DB),
but the new `RoomMoodlightManager` (step 5) is **pure logic** — so added
`RoomMoodlightManagerTest` (4 tests) covering: empty column → 3 default presets;
a parsed segment overriding a default; serialize emitting one segment per preset
with ids renumbered 1..N; and an overridden preset surviving a serialize→parse
round-trip. This locks in the exact `moodlight_data` column format that step 5
moved verbatim. Suite is now 418 tests.

## Verification
- `mvn test` green: 418 tests, 0 failures, JDK 25 (all steps).
