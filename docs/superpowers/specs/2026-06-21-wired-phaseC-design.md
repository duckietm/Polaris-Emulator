# Wired Phase C — implementation design (105 MISSING interactions)

**Date:** 2026-06-21 · from a 10-agent read-only design study (each MISSING effect/condition
mapped to a concrete emulator API + a reuse-vs-new-client-code verdict). Companion to
`2026-06-21-wired-study.md`. **No client furnidata was available**, so each item is tagged by
whether it can ship server-only or needs the client.

## Verdict totals (85 designed)
- **30 canDoNow** — reuse an EXISTING client dialog code AND the emulator API exists → shippable now.
- **48 needsClientCode** — emulator behaviour feasible but the box needs a NEW client dialog
  (a new `interaction_type` furnidata entry, sometimes a new `WiredEffectType`/`WiredConditionType` code).
- **7 notFeasible** — no server API/storage at all (need new subsystems).

## The proven "reuse" pattern (why canDoNow works without the client)
A new effect can reuse `WiredEffectType.SHOW_MESSAGE` (code 7): its client dialog is a single text
field + a user-source selector. The server drives the dialog via `getType().code`, so any effect that
needs *one value + who-it-targets* can piggyback it — **already done in production** by
`WiredEffectGiveRespect`, `WiredEffectGiveEffect`, `WiredEffectGiveHotelviewBonusRarePoints`.
Conditions similarly reuse existing condition dialog shapes (`TEAM_HAS_SCORE`, `ACTOR_WEARS_EFFECT`,
`HAS_ALTITUDE`, `MATCH_SSHOT`). Template to copy: `effects/WiredEffectGiveRespect.java`.

## canDoNow (30) — implement now, reusing existing dialogs

### ✅ DONE (committed)
- `wf_act_give_credits` → `Habbo.giveCredits(amount)` · `wf_act_give_duckets` → `givePixels(amount)`
  · `wf_act_give_diamonds` → `givePoints(seasonal.currency.diamond=5, amount)`. All via SHOW_MESSAGE,
  amount capped by `WiredNumericInputGuard.maxRewardAmount()`. Test: `WiredAliasResolutionTest`.

### Effects (reuse SHOW_MESSAGE unless noted)
- `wf_act_give_badge` / `wf_act_give_userbadge` → `BadgesComponent.createBadge(code,h)` +
  `AddUserBadgeComposer`; guard `hasBadge`. String field = badge code (one class can back both).
- `wf_act_remove_badge` → `removeBadge(code)` + `BadgesComponent.deleteBadge(userId,code)` +
  `InventoryBadgesComposer` + `UserBadgesComposer`. (recipe: `TakeBadgeCommand`)
- `wf_act_give_achievement` → `AchievementManager.progressAchievement(h, getAchievement(name), amount)`;
  string = achievement name.
- `wf_act_give_experience` → `HabboStats.addAchievementScore(amount)` (caveat: no live composer; updates on reload).
- `wf_act_sit` → `Room.makeSit(h)` · `wf_act_lay` → replicate `LayCommand` (set `cmdLay`,
  `RoomUnitStatus.LAY`, check 3 tiles in front); gate both with `RoomUnit.canForcePosture()`. Reuse a
  user-source-only dialog (TOGGLE_STATE/KICK_USER shape).
- `wf_act_make_fast_walk` → `RoomUnit.setFastWalk(bool)`.
- `wf_act_walk_to_furni` → `RoomUnit.setGoalLocation(tile of resolved furni)` (clone of
  `WiredEffectBotWalkToFurni`, reuse TELEPORT dialog).
- `wf_act_make_user_say` → `Room.talk(h, RoomChatMessage, TALK, ignoreWired=true)` (reuse Whisper/SHOW_MESSAGE).
- `wf_act_say_command` → `CommandHandler.handleCommand(h.getClient(), ":cmd")` (reuse SHOW_MESSAGE).
- `wf_act_log` → SLF4J `LOGGER.info` + `WiredTextPlaceholderUtil` (reuse SHOW_MESSAGE, no targets).
- `wf_act_open_habbo_pages` → `InClientLinkComposer(link)` (reuse SHOW_MESSAGE).
- `wf_act_toggle_moodlight` → `room.getMoodlightData()` enable/disable + `InteractionMoodLight` items
  (reuse RESET_TIMERS no-param dialog).
- `wf_act_reset_highscores` → `WiredHighscoreManager.setEntriesForItemId(id, [])` +
  `InteractionWiredHighscore.reloadData()` (+ DELETE rows for a persistent reset).
- `wf_act_move_user_tiles` (partial: reuse MOVE_ROTATE_USER, 2nd int = count) — direction+count.
- `wf_act_all_users_leave_team` → `Game.removeHabbo(h)` over all room habbos (reuse LEAVE_TEAM).

### Conditions (reuse an existing condition dialog shape)
- `wf_cnd_not_habbo_has_credits` → `HabboInfo.getCredits() < amount` · `not_habbo_has_diamonds` →
  `getCurrencyAmount(5)` · `not_habbo_has_duckets` → `getCurrencyAmount(0)`. Reuse TEAM_HAS_SCORE (code 34)
  amount+comparison+source+quantifier.
- `wf_cnd_freeze` / `wf_cnd_not_freeze` → `WiredFreezeUtil.isFrozen(unit)` (reuse ACTOR_WEARS_EFFECT shape).
- `wf_cnd_furni_in_range` / `not_in_range` → `RoomTile.distance` vs trigger tile, radius reused from the
  HAS_ALTITUDE numeric field; furni via `WiredSourceUtil.resolveItems`.
- `wf_cnd_has_same_height` / `not_has_same_height` → compare `HabboItem.getZ()` across selected furni
  (reuse MATCH_SSHOT furni picker).

## needsClientCode (48) — emulator-feasible, but need a new client dialog
**These are ready to write as soon as the furnidata / wired definitions arrive.** All have working
emulator APIs; the only blocker is the client dialog (new `interaction_type` and usually a new
`WiredEffectType`/`WiredConditionType` code). Highlights:
- **Identity conditions** (full API, tiny client work): `habbo_is_male`/`is_female` (`HabboInfo.getGender`),
  `motto_contains` (`getMotto`), `habbo_has_rights`(+not) (`Room.hasRights`), `habbo_owns_furni`(+not) /
  `has_at_least_x_items` (`ItemsComponent.getItems/itemCount`, inventory-only), **`habbo_owns_badge`(+not)**
  (`BadgesComponent.hasBadge` — the correct OWNED check the Phase-A alias deliberately skipped).
- **Appearance effects**: `give_look` (`HabboInfo.setLook` + `UpdateUserLookComposer`/`RoomUserDataComposer`),
  `give_prefix` (full `PrefixesComponent` system), `give_name_color` (modelled as a coloured prefix).
  `remove_look` is *partial* — no prior-look storage exists.
- **Tags**: `has_tag`/`not_has_tag` read `HabboStats.tags` (feasible). The `add_tag*`/`remove_tag`
  EFFECTS are *partial* — there is **no write/persist API** for tags (`HabboStats.run()` omits the column);
  needs a small `persistTags()` helper (UPDATE users_settings.tags).
- **Furni manipulation** (all via `HabboItem.setExtradata` + `Room.updateItemState`): `set_state`,
  `set_trg_state`, `color_furni`, `open_gates`/`close_gates`, `close_dice`, `roll_dice` (reuse
  `RandomDiceNumber`). Server-trivial; just need the picker dialogs.
- **Spatial conditions**: `user_in_range`(+not), `user_on_furni_with_state`, `trg_frn_adjacent_state` —
  APIs exist (`RoomTile.distance`, `Room.getItemsAt`, `getExtradata`), need a radius/state dialog.
- **Negative effects**: `neg_log`, `neg_show_message` — need a new `NEG_*` `WiredEffectType` registered in
  `WiredEngine.isNegativeConditionEffect`.
- **Misc**: `forward_user_to_room` (`ForwardToRoomComposer` + `RoomManager.enterRoom`),
  `give_or_take_furni` (create/delete inventory furni), `set_room_ad` (`InteractionRoomAds`),
  `x_points_leaderboard`(+not) (username-scan of highscore rows), `user_cooldown`/`daily_trg`/`first_trg`
  (need write-on-pass persisted state).

## notFeasible (7) — need new server subsystems
`give_daily_task_progress` (no daily-task system), `set_furni_opacity`, `enable/disable_click_through`,
`hide_trg_item` (opacity/click-through/per-item visibility are unmodelled server-side),
`game_mode_on`/`game_mode_off` (no game-mode state). Each needs a new server attribute + composer field
+ client furnidata.

## Recommended order
1. **DONE:** currency (credits/duckets/diamonds).
2. Next canDoNow batch (no furnidata needed): badges (give/remove/userbadge), give_achievement, sit/lay,
   make_user_say, say_command, log, open_habbo_pages, walk_to_furni, fast_walk, toggle_moodlight,
   reset_highscores, all_users_leave_team; conditions freeze/not_freeze, currency checks, range, same-height.
3. After the furnidata arrives: the needsClientCode set (identity conditions + owns_badge first — biggest
   value, smallest client work; then appearance/tags/furni-fx/forward).

**Validation:** every wired furni still needs an in-room smoke test (place → configure → save → trigger).
The currency effects are the proof-of-concept for the SHOW_MESSAGE-reuse approach — confirm those live
before mass-producing the rest.
