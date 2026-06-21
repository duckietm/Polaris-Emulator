# Nitro client — wired dialog patch list

**Date:** 2026-06-21. What the Nitro CLIENT would need to add so the remaining wired boxes get a
**proper** dialog. Context: in Nitro the wired editor dialog is chosen by the **interface code** the
server sends in the wired-data composer (= the emulator's `WiredEffectType.code` /
`WiredConditionType.code`); the client maps that code to a dialog layout. So a "new dialog" = a new code
the Nitro wired UI knows how to render. The server side for everything below is already done (or noted).

The emulator codes already in use ARE the standard Nitro codes (the smoke test confirmed e.g. code 7 =
show-message text dialog renders correctly). Pick **unused** code numbers for the new dialogs.

## 0. No patch needed — works today (smoke-test confirmed)
~98 of the new interactions reuse an existing dialog where the field is meaningful, and work live:
currency (give_credits/duckets/diamonds), badges (give/remove/userbadge), give_achievement,
give_experience, give_look, say_command, make_user_say, open_habbo_pages, log, add/remove tag, sit, lay,
make_fast_walk, walk_to_furni, move_user_tiles, toggle_moodlight, reset_highscores, all_users_leave_team,
neg_show_message, neg_log, the dance/idle triggers, and the conditions (currency-lacks, freeze, range,
same-height, has_tag, owns_badge, motto_contains, has_at_least_x_items). **These need nothing from Nitro.**

## 1. Dialog wart — optional UX patch (works now, but the reused dialog shows an irrelevant field)
| Furni | Server uses code | What the user sees now | Ideal dedicated dialog |
|---|---|---|---|
| `wf_cnd_habbo_owns_furni` / `wf_cnd_habbo_not_owns_furni` | `HAS_ALTITUDE` | furni picker **+ an unused "altitude" number** | furni-TYPE picker + quantifier (ANY/ALL), **no number** |

Server already implemented; functional. A dedicated code (furni-picker + quantifier, no numeric) would
just hide the stray number field.

## 2. Exists client-side, needs a dedicated dialog (server ready, blocked only on the client UI)
| Furni | Behaviour | Dialog fields the client must collect | Server status |
|---|---|---|---|
| `wf_act_set_state` | set a furni to a specific state index | furni picker **+ integer target-state** | trivial server-side once a code carries both. NOTE: `wf_act_match_to_sshot` (already working) covers the same need via a saved snapshot, so this is low priority. |

## 3. Absent client-side — Nitro needs BOTH a furni definition AND a dialog
These classnames are **not** in `FurnitureData.json`, so Nitro can't render them at all. To support them,
add the furni (classname + sprite, specialtype like the other wired furni) AND a dialog. Server-side each
is feasible (APIs exist) — implement the emulator class only once the client can place+configure it.

- `wf_act_color_furni` — furni picker + colour/state index.
- `wf_act_open_gates` / `wf_act_close_gates` — furni picker, no extra field (fixed open/close).
- `wf_act_close_dice` / `wf_act_roll_dice` — furni picker, no extra field.
- `wf_act_give_name_color` / `wf_act_give_prefix` — text (colour/prefix) + user source. (server: needs the
  prefix system wiring; modest.)
- `wf_act_forward_user_to_room` — integer room-id + user source.
- `wf_cnd_habbo_is_male` / `wf_cnd_habbo_is_female` — **no input** (just user source + quantifier).
- `wf_cnd_habbo_has_rights` / `wf_cnd_not_habbo_has_rights` — **no input** (user source + quantifier).
- `wf_act_give_or_take_furni` — furni-type picker + give/take toggle + user source.
- `wf_act_set_furni_opacity` — furni picker + 0–255 opacity. **Server-blocked too**: opacity is a pure
  client visual the emulator never models; would also need a new server furni attribute + composer field.

## 4. Not feasible without new server subsystems (skip)
`wf_act_give_daily_task_progress` (no daily-task system), `wf_act_enable/disable_click_through`,
`wf_act_hide_trg_item` (per-furni visibility/click-through unmodelled), `wf_act_game_mode_on/off`
(no game-mode state). Each needs a new server subsystem before any client work is worthwhile.

## How to wire a new dialog end-to-end (reference)
1. **Nitro:** add the furni to furnidata (if absent) + a wired dialog component keyed to a new code N.
2. **Emulator:** add `WiredEffectType.X(N)` / `WiredConditionType.X(N)`; implement the
   effect/condition class (the recipe is in `2026-06-21-wired-study.md`); register it in `ItemManager`;
   its `serializeWiredData` sends code N so the client renders dialog N; `saveData` parses the new fields.
3. **DB:** an `items_base` row with `interaction_type = wf_..._...` (most already exist in `next`).
