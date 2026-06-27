# Wired Phase-1 — deferred features design notes

Status of the gap-analysis "Phase 1" set (see `habbofurni-gap-analysis.md`). Ground-truthing the
real code showed the "~1 day, low/medium effort" estimate held only for the add-ons; the furni-
manipulation effects each carry a real design or engine question. This file records the findings and
the design needed for the three deferred ones.

_Last updated: 2026-06-27._

## Done (implemented + built + 464 tests green + migration 016)

| Furni | Server class | Code | Client view |
|---|---|---|---|
| `wf_xtra_mov_curve` | `WiredExtraMovementCurve` | extra CODE 97 | `WiredExtraMovementCurveView` |
| `wf_xtra_var_time_util` | `WiredExtraTimeUtilities` | extra CODE 98 | `WiredExtraTimeUtilitiesView` |
| `wf_act_move_furni_as_group` | `WiredEffectMoveFurniAsGroup` | `WiredEffectType` 95 | `WiredActionMoveFurniAsGroupView` |
| `wf_slc_remote` | `WiredEffectRemoteSelector` | `WiredEffectType` 96 | `WiredSelectorRemoteView` |

Notes on the two judgement calls:
- **mov_curve / var_time_util** are faithful **config-holder add-ons** (the `WiredExtraAnimationTime`
  family pattern): they open + persist a setting and expose a getter (`getCurveType()` /
  `getTimeUnit()`). Wiring those getters into the actual movement-easing math / variable-time output
  is a follow-up — same "level of done" as `AnimationTime`, which is itself just a duration holder.
- **slc_remote** "remote selection" is not defined in the legacy enum. Implemented as the most
  defensible reading: a read-only selector that resolves targets from an incoming wired SIGNAL (the
  "remote"), with a picked-furni fallback. Safe (selectors never mutate room state). Refine if a
  different behaviour is intended.

## Deferred — need a design decision before implementing

### 1. `wf_act_click_conf` — "Set Clicking Configurations"
**Blocker:** click-through / walkthrough is `Item.allowWalk()` — a **base-item** column
(`items_base.allow_walk`) read at load. There is **no per-instance `HabboItem` setter**. Toggling it
on one placed furni at runtime needs:
- a new per-instance override field on `HabboItem` (e.g. `walkableOverride: Boolean`),
- threading it through the item serialization (`HabboItem.serializeExtradata` at ~line 142 appends
  `getBaseItem().allowWalk()` — would need to honour the override),
- and through the room walk/stack logic that consults `allowWalk()`.

This is an **engine change**, not a wired-effect clone. Scope it as: add the override + a getter that
prefers it over the base flag, update the ~2 read sites, then a trivial `WiredEffectClickConf` that
flips the override on the selected furni. Medium engine risk; needs its own test pass.

### 2. `wf_act_place_furni` — place a temporary furni
**Blocker:** `ItemManager.createItem()` **persists** a furni (it's how gifts are made) — there is no
temp/ephemeral furni primitive. Spawning one per trigger would write DB rows + pollute the owner's
inventory, and they'd survive restarts.
**Design needed:** a temp-furni lifecycle — e.g. an in-memory `HabboItem` with a synthetic negative
id, added to the room's floor-item maps but **never** persisted, tracked in a per-room registry, and
cleaned up on room unload + by `wf_act_remove_furni`. Must verify the room/packet code tolerates a
non-DB-backed item (placement composer, save-on-unload skip, pickup path). This is the largest piece.

### 3. `wf_act_remove_furni` — remove furni
**Coupled to #2.** On real user furni, `removeHabboItem`/`pickUpItem` is **data loss**. It must be
hard-gated to only remove **wired-placed temp furni** (the synthetic-id items from #2), never user
furni. Build it together with `place_furni` once the temp registry exists. Implementing it standalone
is unsafe.

## Recommended order when resumed
1. `click_conf` (self-contained engine override; smallest of the three).
2. `place_furni` + `remove_furni` together (temp-furni registry, then both effects).

The trigger/effect registration + client-dialog plumbing is identical to the 4 already shipped — only
the engine pieces above are new.
