# Furni Editor — create furnidata entry if missing (upsert)

**Date:** 2026-06-13
**Status:** Approved design → implementation
**Repos:** Arcturus-Morningstar-Extended (emulator, primary), Nitro-V3 (client, minor)

## Problem

In the in-client **Furni Editor**, many furni have **no matching entry in the
furnidata** (split-tier `*.json5` files). Today the editor detects this
(`furniDataEntry === null`), shows a "Public Name (DB fallback)" and **locks**
the name/description fields with the warning "this furni has no matching
furnidata entry … so its display name can't be edited here."
`FurnidataWriter.write()` is **edit-only** — it refuses classnames absent from
furnidata. There is no path to **create** the missing entry.

Goal: let an operator create the missing furnidata entry directly from the
editor, so the furni gets a real, editable name/description.

## Decisions (from brainstorming)

1. **Trigger = upsert Save.** When the entry is missing, the name/desc fields
   are *unlocked* (name prefilled from the DB Public Name); the existing "Save
   name/desc" creates the entry if absent, edits it if present. No separate
   button beyond a relabel ("Create entry" when missing).
2. **Completeness = full entry seeded from `items_base`.** The created entry is
   a complete furnidata object (structural fields read from the item's DB row),
   not a name-only stub.
3. **Target = config key `furnidata.editor.create_tier` (default `custom`).**
   Split-tier → that tier file; single-file furnidata → the single file.

## Approach

**Reuse the existing `FurniEditorUpdateFurnidata` packet** (outgoing header
`10046`, result `10044`) and make the **server handler upsert**. Rejected
alternative: a dedicated `Create` packet (10050) — unnecessary, because the
create needs **no extra client-supplied fields** (the server reads `items_base`
for the structural fields and takes name/desc from the existing 10046 payload).

**Net wire impact: none.** No renderer changes, no new packet. Only:
- Emulator: a new `FurnidataWriter.create(...)` + the 10046 handler becomes
  upsert + one config key + an `items_base → furnidata` field mapper.
- Client: unlock the name/desc fields when the entry is missing + relabel Save.

## Emulator changes (Java)

### 1. `habbohotel/items/FurnidataWriter.create(...)`
New method, mirrors `write()`'s safety (locate target file, **backup +
atomic write**, preserve JSON5 formatting/comments):
- Resolve target file: read config `furnidata.editor.create_tier` (default
  `custom`). If split-tier (manifest present) → that tier's file (create the
  file with a valid empty-array JSON5 shell if it doesn't exist yet). If
  single-file furnidata → the single file.
- Append a complete entry object (see field mapping) to the correct array
  (`roomitemtypes` for floor / `wallitemtypes` for wall).
- **Guards:** refuse if the classname already exists anywhere in furnidata
  (caller routes to edit instead); refuse if the chosen `id` (sprite id) is
  already used by a *different* classname (id collision would break
  `roomItem.name.{id}` / `getFloorItemData(typeId)` resolution).
- Return a result enum/boolean (created / already-exists / id-collision /
  io-error) so the handler can message the operator precisely.

### 2. `FurniEditorUpdateFurnidataEvent` (header 10046) → upsert
- Resolve classname + the full `items_base` row from `itemId` (handler already
  resolves classname).
- If furnidata **has** the classname → existing edit path (`write()`).
- Else → build the complete entry from `items_base` + submitted name/desc →
  `FurnidataWriter.create(...)`.
- After either path (unchanged from edit): `FurnitureTextProvider.reindexFromSource()`,
  broadcast `FurnitureDataReloadComposer` (10047), mirror name into
  `items_base.public_name`, audit log (action `"create"` vs `"edit"`), respond
  `FurniEditorResultComposer` (10044) with success/precise error.
- Permission `ACC_CATALOGFURNI` + 1000ms rate-limit (unchanged).

### 3. Config key
`furnidata.editor.create_tier` (default `custom`), read where the writer
resolves the target file.

### 4. `items_base → furnidata` field mapping (helper)
Read the item's DB definition and map to furnidata JSON. Minimum complete set
(exact column/field names verified during implementation against the
`FurnidataReader` schema + `items_base`):
- `id` = item **sprite id** (the visual/type id — MUST match so the furni
  resolves its name/data), `classname` = `item_name`,
- `type` = `"s"` (floor) / `"i"` (wall) from the item type,
- `name` = submitted name (fallback: public_name → classname), `description` =
  submitted description,
- `xdim`/`ydim` = width/length, `canstandon`/`cansiton`/`canlayon` from the
  item's stand/sit/lay flags, plus the standard furnidata defaults for the
  remaining fields (`partcolors`, `offerid = -1`, `buyout`, `bc`,
  `excludeddynamic`, `customparams`, `specialtype`, `furniline`,
  `environment`, `rare`, `revision`, `category`).

## Client changes (React) — `FurniEditorEditView.tsx`

- When `furniDataEntry === null`: **unlock** the name/description inputs
  (currently gated by the `furnidataEditable` memo), prefill name from
  `item.publicName`, description blank. Replace the "can't be edited here"
  warning with an informational note: "No furnidata entry yet — saving will
  create one in the «custom» tier." Relabel the Save button to "Create entry"
  while missing.
- The Save handler is unchanged — it already sends
  `FurniEditorUpdateFurnidataComposer(itemId, { name, description })`.
- On `FurniEditorResultEvent` success, re-fetch detail
  (`FurniEditorDetailComposer(itemId)`) so `furniDataEntry` populates and the UI
  flips to normal edit mode.

## Data flow

```
Save (entry missing)
  → 10046 UpdateFurnidata(itemId, {name, desc})
  → handler: classname absent → build complete entry from items_base + name/desc
  → FurnidataWriter.create(...) into the custom tier (atomic + backup)
  → reindexFromSource() + broadcast 10047 FurnitureDataReload
       → every client's catalog/inventory/infostand refreshes; the rendered
         furni now resolves its real name
  → mirror items_base.public_name
  → audit "create"
  → 10044 result(success)
  → client re-fetches detail → entry now present → normal edit mode
```

## Error handling / edge cases

- Classname already present (lookup race) → routed to edit (upsert).
- Sprite id already used by a different classname → refuse + "id N already
  used by classname X".
- `items_base` row missing → refuse + error (shouldn't happen for a known item).
- Tier file absent → created with a valid JSON5 shell.
- Empty submitted name → fall back to public_name, else classname.
- Concurrency: reuse `write()`'s file lock + atomic write + backup.

## Testing

- **Emulator unit:** `FurnidataWriter.create` writes a valid JSON5 entry into
  the target tier; idempotency guard (already-exists); id-collision guard;
  round-trips through `FurnidataReader`.
- **Runtime (Chrome handle available):** in the Furni Editor select a furni
  with no furnidata entry (the live "DB fallback" case), type a name, Save →
  entry created, furni name updates live (10047 broadcast), reopen → entry
  present and editable. Verify the new object lands in the `custom` tier file
  and `FurnidataReader` parses it.

## Out of scope

- No new wire packet; no renderer changes.
- No bulk/batch creation; one furni at a time via the editor.
- No editing of structural fields from the UI (only name/desc, as today); the
  structural fields are seeded once at creation from `items_base`.
- No deletion of furnidata entries (separate concern).
