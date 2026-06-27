# Wired furni — habbofurni.com gap analysis (refreshed 2026-06-27)

**Method.** Cross-referenced the habbofurni.com wired catalogues — `wired` (141, 2 pages),
`wired_condition` (24), `wired_effect` (28), `wired_trigger` (15) — against our
`ItemManager.loadItemInteractions()` registrations (**330 distinct `wf_*` classes**) and
`next.items_base` (**474 distinct `wf_*` rows**). The furni's `items_base.interaction_type` decides
which class loads it; `'default'` → `InteractionDefault` = **inert**. Note many furni are functional
via an **alias** — their `interaction_type` points at a different existing class, not a same-named one.

## TL;DR

| | count |
|---|---|
| habbofurni wired universe (excl. `test_*` dev furni) | **191** |
| Functional on our side | **168 (~88%)** — 155 dedicated class + 12 alias-to-existing-class + 1 decorative |
| **Real gaps** (load inert as `default`, need implementation) | **23** |

The 23 real gaps are *exactly* the deferred Phase-1 effects + all of Phase 2. Every "classic" wired
trigger / condition / effect / selector / add-on is covered. What's missing is the gen-3 "Origins"
feature set: **chest/storage, transactions, contracts, quests**.

## ✅ Done this session — Phase 1 (4 of 7), end-to-end + migration 016

| Furni | Class | Code |
|---|---|---|
| `wf_xtra_mov_curve` | `WiredExtraMovementCurve` | extra 97 |
| `wf_xtra_var_time_util` | `WiredExtraTimeUtilities` | extra 98 |
| `wf_act_move_furni_as_group` | `WiredEffectMoveFurniAsGroup` | effect 95 |
| `wf_slc_remote` | `WiredEffectRemoteSelector` | selector 96 |

Server `mvn clean test` 464 green; client `npx vite build` green; migration 016 applied to
`next`+`amx_test` (needs an emulator restart to load). Earlier migrations 013/014/015 fixed the
wrong-`interaction_type` furni.

## ❌ The 23 real gaps (interaction_type = 'default')

### Phase-1 deferred — need engine work (3), see `phase1-deferred-design.md`
`wf_act_click_conf` (no per-instance walkthrough setter), `wf_act_place_furni` + `wf_act_remove_furni`
(need a temp-furni lifecycle; `createItem` persists → DB-spam / data-loss risk).

### Phase 2 — Chest / Storage (10)
`wf_storage_coins1`, `wf_storage_coins2`, `wf_storage_furni1`, `wf_storage_furni2`,
`wf_storage_furni_starter`, `wf_act_give_currency`, `wf_act_give_furni`, `wf_cnd_chest_has_items`,
`wf_cnd_chest_has_item_type`, `wf_xtra_scan_chest_furni_by_type`. A persisted chest-contents model
(new table + capacity) + give/scan logic. **Prerequisite of transactions/contracts.**

### Phase 2 — Transactions (4)
`wf_act_init_transaction`, `wf_act_cancel_transaction`, `wf_trg_transaction_complete`,
`wf_trg_transaction_fail`. Atomic exchange state machine; the complete/fail triggers reuse the
additive-trigger plumbing proven by `dice_rolled`/`press_keybind`.

### Phase 2 — Contracts (4)
`wf_contract_trade`, `wf_contract_reward`, `wf_contract_payment`, `wf_xtra_custom_contract`. Contract
definitions (trade/reward/payment terms) that drive the transaction system.

### Phase 2 — Quests (2)
`wf_var_quest`, `wf_var_quest_chain`. Quest/quest-chain variables with persisted progress; extend the
existing wired-variable subsystem (`WiredExtra*Variable`, values in `room_*_wired_variables`).

## 🟢 Not gaps — functional already (no work needed)

12 furni look missing (no same-named class) but their `interaction_type` aliases a working class:

| Furni | interaction_type → behaviour |
|---|---|
| `wf_act_teleport_to_room` | `wf_act_forward_user_to_room` (cross-room teleport already works) |
| `wf_room_linker` | `teletile` |
| `wf_antenna1`, `wf_antenna2` | `antenna` (signal system matches by name; migration 015) |
| `wf_arrowplate`, `wf_arrowplate_8dir` | `pressureplate` |
| `wf_blob2` | `wf_blob` (BattleBanzai) |
| `wf_box` | `puzzle_box` |
| `wf_proto_pyramid` | `pyramid` |
| `wf_ltdproto_act_toggle_state` | `wf_act_toggle_state` (Ancient prototype → real class) |
| `wf_proto_trg_at_given_time` | `wf_trg_at_given_time` |
| `wf_proto_cnd_trggrer_on_frn` | `wf_cnd_trggrer_on_frn` |

Plus `wf_maze` (`default`) — decorative divider, fine as-is.

## Recommendation / order
1. ✅ Phase 1 (4 furni) — done.
2. Phase-1 deferred (3) — engine work, scoped in `phase1-deferred-design.md`.
3. **Phase 2 — Chest/Storage first** (it's the largest and a prerequisite for transactions/contracts),
   then transactions+contracts (coupled), then quests. Each needs a data model + DB table; scope one
   area at a time with an in-room smoke test.
