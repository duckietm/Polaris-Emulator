# Wired furni — habbofurni.com gap analysis & integration study

**Method.** Cross-referenced the four habbofurni.com wired catalogues (`wired` = 141 items / 3 pages,
plus `wired_trigger`, `wired_effect`, `wired_add_on` views) — **186 distinct wired classnames** — against
`next.items_base` (484 distinct `wf_*` furni) and the registered interaction classes in
`ItemManager.loadItemInteractions()` (465 registrations). The furni's `items_base.interaction_type`
column decides which class loads it; `'default'` (or a wrong value) → `InteractionDefault` = **inert**.

## TL;DR
We already **own essentially every wired furni** habbofurni lists. The gap is not furni — it's a set of
**advanced/newer wired FEATURES with no server implementation**. Concretely:

| Bucket | Count | Status |
|---|---|---|
| habbofurni wired furni we have in items_base | **183 / 186** | ✅ |
| Furni name truly absent | 3 | ⚠️ covered by variants (below) |
| Present but inert (`interaction_type='default'`/wrong) | 43 | split below |
| → fixed by **migration 015** (registered class, wrong type) | 8 | ✅ done |
| → decorative / game pieces (`'default'` is fine) | ~12 | ✅ ok |
| → **advanced features needing implementation** | ~23 | ❌ the real gap |

## Furni "missing" (3) — not real gaps
- `wf_colorwheel` — the colour-wheel **interaction works** via `habbowheel` / `ads_lin_wh_c` /
  `ads_tlc_wheel` (`interaction_type='colorwheel'` → `InteractionColorWheel`). Only the literal name is absent.
- `wf_act_furni_to_furni` / `wf_act_user_to_furni` — the **interaction classes ARE registered**
  (`WiredEffectFurniToFurni`, `WiredEffectUserToFurni`, ItemManager:381-382) and a variant furni
  (`wf_act_cnd_furni_to_furni`) exists; only these exact item_names lack a row.

## Fixed now — migration 015 (8 furni → functional after restart)
Registered class existed, row pointed at `'default'`/wrong type (same pattern as 013/014):
`wf_xtra_anim_time`, `wf_xtra_filter_users`, `wf_xtra_mov_carry_users`, `wf_xtra_mov_physics`,
`wf_xtra_text_input_variable` (→ their `WiredExtra*` class); `wf_antenna1/2` → `'antenna'` (the signal
system matches antennas by interaction NAME); `wf_act_unfreeze` → `WiredEffectUnfreeze` (was the wrong
`wf_act_give_prefix`).

## Decorative / game pieces — leave as-is
`wf_wire1..4` (floor wiring, purely visual), `wf_colortile`, `wf_glowball`, `wf_maze`, `wf_pyramid`,
`wf_upcounter2` — these are BattleBanzai/Freeze/game furni driven by game logic, not a wired interaction;
`'default'` (or a dedicated game interaction) is acceptable. Not part of the wired-feature gap.

## THE REAL GAP — advanced wired features to implement (~23 furni, 6 areas)
None of these have a server class today; the furni rows exist but load inert. Ordered by effort.

### Low/medium effort (wired-effect/condition/selector pattern, no new persistence)
- **Placement effects** — `wf_act_place_furni` (place a temp furni), `wf_act_remove_furni` (remove temp
  furni), `wf_act_move_furni_as_group` (move selected furni together). Manipulate room items; mirror
  `WiredEffectMoveFurniTo`. *Temp-furni lifecycle needs care (DB-less placement + cleanup).*
- **`wf_act_click_conf`** — "Set Clicking Configurations" (toggle click-through on selected furni). Akin
  to the `conf_*` control furni already implemented.
- **`wf_slc_remote`** — "Remote selection" selector. Mirror existing `WiredEffect*Selector` classes.
- **Add-ons** — `wf_xtra_mov_curve` (movement curve), `wf_xtra_var_time_util` (time utilities). Mirror
  existing `WiredExtra*` (e.g. `WiredExtraAnimationTime`).

### High effort — need NEW data models + DB tables (gen-3 "Origins"-style wired)
- **Chest / storage system** — `wf_storage_coins1/2`, `wf_storage_furni1/2/starter` (furni that HOLD
  currency/furni), `wf_act_give_currency`, `wf_act_give_furni` (give from chest), `wf_cnd_chest_has_items`,
  `wf_cnd_chest_has_item_type`, `wf_xtra_scan_chest_furni_by_type`. Requires a persisted chest-contents
  model (a `wired_chest_contents` table or extradata-encoded inventory) + give/scan logic.
- **Transactions** — `wf_act_init_transaction`, `wf_act_cancel_transaction`, `wf_trg_transaction_complete`,
  `wf_trg_transaction_fail`. An atomic, possibly multi-user exchange state machine raising the
  complete/fail triggers (mirror the additive-trigger plumbing used for dice_rolled/press_keybind).
- **Contracts** — `wf_contract_trade/reward/payment`, `wf_xtra_custom_contract`. Contract definitions that
  drive the transaction system (trade/reward/payment terms).
- **Quests** — `wf_var_quest`, `wf_var_quest_chain`. Quest/quest-chain variables with persisted progress
  (extends the existing wired-variable subsystem `WiredExtra*Variable`).

## Recommendation
1. ✅ **Migration 015** already lands 8 furni. Restart to apply.
2. **Phase 1 (medium):** the placement effects + `click_conf` + `slc_remote` + the two add-ons — each is a
   single `InteractionWired*` class on proven patterns, `mvn test`-verifiable, no new tables. ~1 day.
3. **Phase 2 (large):** chest/storage, then contracts+transactions (they're coupled), then quests — each
   needs a data model + DB table + careful logic. A multi-day feature project; best scoped one area at a
   time with an in-room smoke test. The trigger halves (transaction_complete/fail) reuse the additive
   trigger plumbing proven this session.
