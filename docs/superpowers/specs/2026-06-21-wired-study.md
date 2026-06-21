# Wired subsystem — study & gap analysis

**Date:** 2026-06-21 · derived from a 6-agent read-only study (architecture + recipe)
plus a DB diff of `next.items_base` against the `ItemManager` wired registry.
**No code changed.** This is the map we use to decide which interactions to add.

## 1. Where things stand (DB vs code)

- DB `next` furnidata exposes **257 distinct wired `interaction_type`s** in `items_base`.
- The emulator registers **168** wired `interaction_type` strings in
  `ItemManager.initInteractions()` → **178 wired classes** total.
- Naïve diff: **177 DB types are not literally registered**. But that number is
  misleading — the emulator and this furnidata use **divergent naming** (e.g. DB
  `wf_act_freeze_habbo` vs code `wf_act_freeze`; DB lacks the `wf_slc_*`/`wf_var_*`/
  `wf_conf_*` families the code uses). After semantic categorization:

| Category | Count | Meaning | Fix cost |
|---|---|---|---|
| **ALIAS** | 30 | Already implemented, only the name differs | ~1 registry line each |
| **VARIANT** | 39 | A parameter/dropdown value of an existing class | mostly ~1 registry line; a few need a param |
| **MISSING** | 105 | No implementation exists | a new class each |
| **UNSURE** | 3 | Needs a closer look | verify first |

- The hotel is currently **near-empty**: `items` has 351 placed furni, **0 wired
  boxes placed**, 341 are `default`. So this gap is about **future building
  capability** (catalog), not furni that are broken in rooms right now.

## 2. How wired works (runtime)

**Two engines in the tree — use the live one.** Live path:
`WiredManager` → `WiredEngine`. The old `habbohotel/wired/WiredHandler.java` is
**legacy/dead** for dispatch (`WiredManager.isExclusive()` hard-returns true).

Flow per fired trigger (`WiredEngine.processStack`,
`wired/core/WiredEngine.java:388-505`):
1. **Trigger** `matches(item, WiredEvent)` (keyword triggers also capture `$` text).
2. Actor requirement check (`requiresActor()`).
3. Build `WiredContext` (auto-seeds targets with actor + trigger item).
4. **Selectors** run first (create-selectors then filter-selectors) so conditions/
   effects can read `ctx.targets()`.
5. **Conditions** — AND by default; OR-operator conditions bucket by type and are
   resolved via `WiredExtraOrEval.matchesMode`. `negateConditions` inverts.
6. **Effects** execute via `execute(WiredContext)`; negative-condition effects
   (`NEG_CALL_STACKS`, `NEG_SEND_SIGNAL`) run only when conditions fail.
7. Gates: execution-limit extra, plugin hook `WiredStackTriggeredEvent`,
   monitor budget.

**Deferral / recursion safety** (`WiredManager.handleEvent`,
`wired/core/WiredManager.java:252-327`): per-thread `EVENT_HANDLING_DEPTH` +
`DEFERRED_EFFECT_EVENTS` queue. Effects that fire other stacks (signals, chained
triggers) **enqueue** via `dispatchEffectTriggeredEvent`; the queue drains FIFO at
depth 1 — flattening deep recursion into breadth-first. `WiredEngine` also has a
per-room recursion cap (`MAX_RECURSION_DEPTH=10`) and a soft per-`roomId:eventType`
rate limit that can `banRoom` runaway wired.

Stacks are indexed per room by `RoomSpecialTypes` / `RoomWiredStackIndex`,
bucketed by `getType()`.

## 3. Recipe — adding ONE new wired effect

Abstract bases live in `items/interactions/` (NOT `.../wired/`); concrete classes in
`items/interactions/wired/{effects,conditions,triggers,extra}/`.

A new **effect** = subclass `InteractionWiredEffect` and implement:
- `execute(WiredContext ctx)` — the logic. Resolve users/items via
  `WiredSourceUtil.resolveUsers/resolveItems(ctx, source)`; room via `ctx.room()`.
- `getType()` → a `WiredEffectType` (its `.code` selects the **client dialog
  layout** — reuse an existing code unless you also ship a new client SWF dialog).
- `saveData(WiredSettings, GameClient)` — read `getIntParams()/getStringParam()/
  getFurniIds()`, validate, enforce `hotel.wired.max_delay`, set fields + `setDelay`.
- `getWiredData()` → `WiredManager.getGson().toJson(new JsonData(...))`.
- `loadWiredData(ResultSet, Room)` → JSON branch (`startsWith("{")`) + legacy/default.
- `serializeWiredData(ServerMessage, Room)` → write fields in the exact order the
  chosen dialog expects, ending with `getType().code`, `getDelay()`, invalid-trigger
  list if `requiresTriggeringUser()`.
- `onPickUp()` (reset) + deprecated `execute(RoomUnit,Room,Object[])` → `return false`.
- **Two public constructors** (reflection): `(ResultSet, Item)` and
  `(int,int,Item,String,int,int)`.

**Register** (the only wiring line): in `ItemManager.initInteractions()`
add `this.interactionsList.add(new ItemInteraction("wf_act_<name>", WiredEffectX.class));`
(name AND class must be unique).

**DB/client**: one `items_base` row with `interaction_type='wf_act_<name>'` and
`item_name='wf_act_<name>'` (name-fallback at `Item.java:107-116` binds even if
`interaction_type=default`), a valid sprite, and a furnidata class so the client
loads the dialog SWF. No wired-registry table; `wired_data` is filled by the generic
save flow (`WiredEffectSaveDataEvent` → `InteractionWired.run()` UPDATE).

Templates: `effects/WiredEffectFreeze.java` (scalar config),
`effects/WiredEffectMatchFurni.java` (furni-list config),
`effects/WiredEffectWhisper.java` (message + visibility).
Conditions: `InteractionWiredCondition.evaluate(ctx)` + `getType()` +
`saveData(WiredSettings)`. Triggers: `InteractionWiredTrigger.matches(item,event)` +
`getType()` (a `WiredTriggerType`, auto-bound to the event system via
`listensTo()`/`fromLegacyType`) + `saveData(WiredSettings)`.

## 4. The categorized gap

### 4a. ALIAS (30) — fix = add a registry alias line (near-zero risk)
Each DB name maps to an existing class with identical behaviour. Examples:
`wf_act_freeze_habbo→WiredEffectFreeze`, `wf_act_unfreeze_habbo→WiredEffectUnfreeze`,
`wf_act_alert_habbo→WiredEffectAlert`, `wf_act_bot_talk_custom→WiredEffectBotTalk`,
`wf_act_move_furni_to_furni→WiredEffectFurniToFurni`,
`wf_act_tp_furni_to_habbo→WiredEffectFurniToUser`,
`wf_cnd_habbo_in_group→WiredConditionGroupMember`,
`wf_cnd_wears_effect→WiredConditionHabboHasEffect`,
`wf_cnd_wears_handitem→WiredConditionHabboHasHandItem`,
`wf_cnd_match_snapshot_new→WiredConditionMatchStatePosition`,
`wf_trg_user_exits_room→WiredTriggerHabboLeavesRoom`,
`wf_trg_cnd_collision→WiredTriggerCollision`, `wf_pyramid→InteractionPyramid`.
*Caveat:* `*_owns_badge` is mapped to the **wears**-badge class (checks worn, not
owned) — verify intent before aliasing.

### 4b. VARIANT (39) — fix = alias to a base class whose param already covers it
e.g. `wf_act_raise_furni`/`lower_furni→WiredEffectSetAltitude` (increase/decrease),
`wf_act_toggle_state_down→WiredEffectToggleFurni` (TOGGLE_PREVIOUS),
`wf_act_rotate_habbo→WiredEffectMoveRotateUser`,
`wf_act_match_to_sshot_height→WiredEffectMatchFurni` (altitude flag),
`wf_act_show_message_room→WiredEffectWhisper` (ALL_ROOM_USERS),
`wf_cnd_habbo_is_dancing→WiredConditionUserPerformsAction` (DANCE),
`wf_trg_exact_keyword/says_command→WiredTriggerHabboSaysKeyword` (matchMode).
A few are "Extended superset" and may not be 100% covered (`give_score_pp/room`,
`bot_give_handitem_or_effect`, `dont_chase`) — verify per item.

### 4c. MISSING (105) — need new classes. Grouped by theme:
- **Currency / reward effects (HIGH demand):** give_credits, give_diamonds,
  give_duckets, give_experience, give_points_type, give_badge, give_userbadge,
  give_achievement, remove_badge, give_daily_task_progress.
- **User appearance/identity:** give_look, remove_look, give_name_color, give_prefix.
- **User posture/movement:** lay, sit, make_fast_walk, move_user_tiles,
  walk_to_furni, make_user_say, say_command.
- **Tags:** wf_act_add_tag(_perm), remove_tag; wf_cnd_(not_)has_tag.
- **Currency conditions:** not_habbo_has_credits/diamonds/duckets.
- **Identity conditions:** habbo_is_male/female, motto_contains, habbo_has_rights
  (+not), habbo_(not_)owns_furni, habbo_has_at_least_x_items.
- **Proximity conditions:** furni_in_range(+not), user_in_range(+not).
- **Team-colour teleports:** teleport_red/blue/green/yellow/all (classic game tele pads).
- **Furni manipulation effects:** color_furni, set_furni_opacity, set_state,
  set_trg_state, toggle_moodlight, open_gates/close_gates, close_dice/roll_dice,
  set_room_ad, enable/disable_click_through, allign_furni_stack, unhide_items,
  hide_trg_item, move_furni_from_stack.
- **Misc effects:** log/neg_log, forward_user_to_room, open_habbo_pages,
  play_youtube_sound, quick_bopper, reset_highscores, game_mode_on/off,
  double_click, disable_typing_indicator, neg_show_message.
- **Dance/idle TRIGGERS (CHEAP — event plumbing already fires):** starts_dancing,
  stops_dancing, idles, unidles, anti_afk. `WiredManager.triggerUserStartsDancing/
  Idles/...` already fire from RoomUnitManager; only the **trigger class +
  registration** is missing. Lowest-effort new triggers.
- **Other triggers:** dice_rolled, double_click_furni, click_bot, press_keybind,
  user_gets_handitem, username_as_trigger.
- **Leaderboard/cooldown/once conditions:** x_points_leaderboard(+not),
  user_cooldown, daily_trg, first_trg, freeze/not_freeze (frozen-state check).

### 4d. UNSURE (3)
`wf_cnd_trg_by_user` / `wf_cnd_not_trg_by_user` (maybe `WiredConditionTriggererMatch`
entityType=HABBO — verify), `wf_xtra_condition_change` (semantics unclear).

## 5. Recommended phased approach (to decide together)

- **Phase A — Aliases & variants — ✅ DONE** (`ItemManager`, commits after
  `0d44e4d3`): **56 types bound** = 27 pure aliases + 29 parameter-variants, each
  proven to resolve to the intended class by `WiredAliasResolutionTest` (constructs
  `ItemManager` + `loadItemInteractions()`, no DB; 410 tests green). Deferred from A:
  2× `*_owns_badge` (only a worn-badge class exists → needs a real condition),
  `wf_pyramid` (non-wired furni), and 10 low-confidence/superset variants
  (dont_chase, give_enable, give_score_pp/room, send_bubble,
  bot_give_handitem_or_effect, move_rotate_no_under, not_bot_is_dancing,
  execute_for_users, exec_delay). **Pending: an in-room smoke test** — these are the
  server binding; the variants especially depend on the client SWF save-packet shape.
- **Phase B — Cheap new triggers — ✅ DONE** (4 classes + registration; 411 tests):
  `WiredTriggerHabbo{StartsDancing,StopsDancing,Idles,Unidles}`, each modelled on the
  no-config `WiredTriggerCollision` (client code 11). The events already fire from
  RoomUnitManager/RoomCycleManager and the items_base furni rows exist, so the boxes
  are placeable now. `wf_trg_anti_afk` deferred (ambiguous: unidles vs periodic).
  Remaining cheap triggers worth a look: dice_rolled, double_click_furni, click_bot,
  user_gets_handitem (need a new WiredEvent.Type + a fire site).
- **Phase C — High-demand effects:** currency/reward + posture (sit/lay) + tags +
  identity conditions (gender/motto/rights). Well-understood patterns, one class each,
  each independently testable.
- **Phase D — Harder/uncertain:** proximity/range conditions (distance math),
  team-colour teleports (need a tele-pad concept), leaderboard/cooldown, opacity/
  moodlight/room-ad, forward-to-room. Decide per item whether the client supports it.

**Risk note:** wired runtime is the least unit-tested subsystem. Each added
interaction should get a focused behaviour test + a live in-room smoke test before
it's considered done. Build stays green per increment.
