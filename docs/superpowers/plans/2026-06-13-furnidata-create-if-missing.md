# Furnidata create-if-missing (upsert) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement task-by-task. Steps use checkbox (`- [ ]`).

**Goal:** Let the Furni Editor create a complete furnidata entry for a furni that has none, by making the existing `FurniEditorUpdateFurnidata` (10046) handler an upsert.

**Architecture:** Reuse packet 10046 (no renderer changes, no new packet). Emulator: new `FurnidataWriter.create(...)` (JSON5-preserving append) + handler routes "classname missing → create complete entry from `items_base`" + config key. Client: unlock name/desc when the entry is missing and relabel Save to "Create entry".

**Tech Stack:** Java 21 (Arcturus emulator), Gson/JSON5, React/TS (Nitro-V3 client).

**Spec:** `docs/superpowers/specs/2026-06-13-furnidata-create-if-missing-design.md`

**Environment note:** On this machine furnidata is a SINGLE file `Nitro-Files/nitro-assets/gamedata/FurnitureData.json` (`FurnitureTextProvider.isSourceDirectory()==false`). Plan must also handle split-tier (directory) since the code supports it.

---

## File structure

- Modify: `Emulator/.../habbohotel/items/FurnidataWriter.java` — add `create(...)` + a `CreateResult` enum.
- Create: `Emulator/.../habbohotel/items/FurnidataEntryBuilder.java` — maps an `items_base` row → a furnidata JSON5 object string (floor/wall).
- Modify: `Emulator/.../messages/incoming/furnieditor/FurniEditorUpdateFurnidataEvent.java` — upsert routing.
- Create: `Emulator/src/test/.../FurnidataWriterCreateTest.java` — unit test for create().
- Modify (client): `ui/src/components/furni-editor/views/FurniEditorEditView.tsx` — unlock + relabel + re-fetch.
- Config: `items.furnidata.create_tier` (default `custom`) read in the handler/writer; documented in the spec.

---

### Task 1: Lock the furnidata field map (investigation, no code)

**Files:** read-only.

- [ ] **Step 1:** Read the exact `items_base` columns: `grep -n "items_base" Emulator/.../habbohotel/items/ItemManager.java` then read the `Item` constructor that consumes `SELECT * FROM items_base` (`Item.java`) to list columns (expected: `id`, `sprite_id`, `public_name`, `item_name`, `width`, `length`, `stack_height`, `allow_stack`, `allow_sit`, `allow_walk`, `allow_lay`, `type`, `interaction_type`, …).
- [ ] **Step 2:** Read the renderer floor/wall entry parse to confirm which furnidata fields matter: `renderer/packages/.../FurnitureData.ts` (or wherever `FurnitureDataLoader.parseFloorItems` builds a `FurnitureData`). Note the fields it reads (id, classname, revision, category, name, description, adurl, offerId, buyout, rentOfferId, rentBuyout, bc, excludedDynamic, customParams, specialType, canStandOn, canSitOn, canLayOn, furniLine, environment, rare, + dimensions xdim/ydim).
- [ ] **Step 3:** Write the mapping table into this plan file under Task 3 (replace the TABLE-PENDING marker). Mapping (defaults in parens for fields with no items_base source):
  - `id` ← `items_base.sprite_id` ; `classname` ← `items_base.item_name` ; section `roomitemtypes`(floor)/`wallitemtypes`(wall) ← `items_base.type` (`s`/`i`)
  - `name` ← submitted name (fallback `public_name`→`item_name`) ; `description` ← submitted desc
  - `xdim` ← `width` ; `ydim` ← `length` ; `canstandon` ← `allow_walk` ; `cansiton` ← `allow_sit` ; `canlayon` ← `allow_lay`
  - defaults: `revision`(0) `category`("") `defaultdir`(0) `partcolors`({color:[]}) `offerid`(-1) `buyout`(false) `rentofferid`(-1) `rentbuyout`(false) `bc`(false) `excludeddynamic`(false) `customparams`("") `specialtype`(1) `canlayon` as above `furniline`("") `environment`("") `rare`(false)
- [ ] **Step 4:** Commit the locked map: `git commit -am "docs(plan): lock furnidata field map"`

### Task 2: `FurnidataWriter.create(...)` + unit test (TDD)

**Files:** Modify `FurnidataWriter.java`; Create `FurnidataWriterCreateTest.java`.

- [ ] **Step 1: Failing test** — create `FurnidataWriterCreateTest` that: writes a temp single-file furnidata `{ "roomitemtypes": { "furnitype": [ { "id":1, "classname":"old", "name":"Old" } ] }, "wallitemtypes": { "furnitype": [] } }`, calls `writer.create(entryObjectJson5, FurnitureType.FLOOR, /*id*/2, "newcn")`, then reads it back with `FurnidataReader` and asserts BOTH `old` and `newcn` are present, and that the new entry has id 2.

```java
@Test void createAppendsFloorEntryPreservingExisting() throws Exception {
    Path f = Files.createTempFile("furnidata", ".json5");
    Files.writeString(f, "{\n  // comment\n  \"roomitemtypes\": { \"furnitype\": [ { \"id\": 1, \"classname\": \"old\", \"name\": \"Old\" } ] },\n  \"wallitemtypes\": { \"furnitype\": [] }\n}");
    FurnidataWriter w = new FurnidataWriter(f, false, 10_000_000L, 3);
    String entry = "{ \"id\": 2, \"classname\": \"newcn\", \"name\": \"New\", \"description\": \"\" }";
    FurnidataWriter.CreateResult r = w.create("newcn", 2, FurnitureType.FLOOR, entry);
    assertEquals(FurnidataWriter.CreateResult.CREATED, r);
    var entries = new FurnidataReader(f, 10_000_000L).read();
    assertTrue(entries.stream().anyMatch(e -> e.classname().equals("old")));
    assertTrue(entries.stream().anyMatch(e -> e.classname().equals("newcn") && e.id() == 2));
    assertTrue(Files.readString(f).contains("// comment")); // JSON5 comment preserved
}
```

- [ ] **Step 2: Run, expect FAIL** (method missing): `cd Emulator && mvn -q -Dtest=FurnidataWriterCreateTest test` → FAIL/compile error.
- [ ] **Step 3: Implement `create()` + `CreateResult`.** Add to `FurnidataWriter`:
  - `public enum CreateResult { CREATED, ALREADY_EXISTS, ID_COLLISION, NO_TARGET, IO_ERROR }`
  - `public CreateResult create(String classname, int id, FurnitureType type, String entryObjectJson5)`:
    1. `cn = classname.trim().toLowerCase`. Scan all entries via `FurnidataReader(allFiles).read()`: if any entry has `cn` → return `ALREADY_EXISTS`; if any entry has the same `id` but a different classname → return `ID_COLLISION`.
    2. Resolve target file: single-file → `source`; split-tier → the configured create tier file (passed in via a `Path targetFile` arg OR resolved here from `items.furnidata.create_tier`; the handler passes the resolved tier dir's first file). If none → `NO_TARGET` (or create the file with a shell — see Step 3b).
    3. Section key = `roomitemtypes` (FLOOR) / `wallitemtypes` (WALL).
    4. Read raw; locate `"<section>"` → its `"furnitype"` → the `[` … `]` array (reuse `matchingClose`/brace helpers, string-aware). Insert the entry object: if array empty → `[ <entry> ]`; else insert `, <entry>` before the closing `]` (preserve indentation). If the section/array is absent in the target file, synthesize it (e.g. add `"roomitemtypes": { "furnitype": [ <entry> ] }` into the root object).
    5. `backup(target)` + `atomicWrite(target, edited)`; return `CREATED`. Wrap IO in try/catch → `IO_ERROR`.
  - Reuse existing `matchingClose`, `lastUnbalancedBrace`, `jsonEscape`, `backup`, `atomicWrite`.
- [ ] **Step 3b:** Add a helper to find the array insertion point: `static int furnitypeArrayClose(String raw, String section)` returning the index of the `]` that closes `<section>.furnitype`, or -1 if absent. String-aware brace/bracket scan starting from the section key match.
- [ ] **Step 4: Run, expect PASS.** Add a 2nd test for `ALREADY_EXISTS` (create "old") and a 3rd for `ID_COLLISION` (create classname "x" with id 1). `mvn -q -Dtest=FurnidataWriterCreateTest test` → PASS.
- [ ] **Step 5: Commit** `git commit -am "feat(furnidata): FurnidataWriter.create — append new entry (JSON5-preserving)"`

### Task 3: `FurnidataEntryBuilder` (items_base row → entry JSON5 string)

**Files:** Create `FurnidataEntryBuilder.java`.

Mapping table: **(filled by Task 1 Step 3)**

- [ ] **Step 1:** Implement `static String build(ResultSet itemsBaseRow, String name, String description)` (or take a typed struct) that returns a JSON5 object string with the mapped fields (use `jsonEscape` for strings; booleans/ints inline). Floor vs wall determined by caller; this just emits the object. Keep field order matching existing entries for readability.
- [ ] **Step 2:** Unit test: feed a fake row (or a small struct), assert the output string parses (Gson) and has `id`, `classname`, `name`, `xdim`, `ydim`, `canstandon`. `mvn -q -Dtest=FurnidataEntryBuilderTest test` → PASS.
- [ ] **Step 3: Commit** `git commit -am "feat(furnidata): items_base → furnidata entry builder"`

### Task 4: Handler upsert — `FurniEditorUpdateFurnidataEvent`

**Files:** Modify `FurniEditorUpdateFurnidataEvent.java`.

- [ ] **Step 1:** Before `writer.write(...)` (line ~122), check existence: `boolean exists = provider.getName(classname) != null || furnidataHasClassname(provider, classname)`. (Add a small helper that reads the source via `FurnidataReader` and checks the classname, since `getName` returns null for entries with empty names too.)
- [ ] **Step 2:** If `exists` → keep current `write()` path (audit action `"edit"`).
- [ ] **Step 3:** Else (missing) → resolve the full `items_base` row for `itemId` (extend `classnameForItem` into a `loadItemBaseRow(itemId)` returning sprite_id/type/width/length/flags/public_name + classname). Determine `FurnitureType` from `type`. Build the entry via `FurnidataEntryBuilder.build(row, nameOrPublic, desc)`. Resolve target tier (config `items.furnidata.create_tier`, default `custom`; for single-file the writer ignores it). Call `writer.create(classname, spriteId, type, entryJson5)`. Map `CreateResult` → success/precise error message (`ALREADY_EXISTS`→fall back to edit; `ID_COLLISION`→"id N already used"; etc.). On `CREATED`: same post-steps as edit (`reindexFromSource` + broadcast 10047 + mirror public_name + audit action `"create"`).
- [ ] **Step 4:** Build the jar: `cd Emulator && mvn -q clean package -DskipTests` → BUILD SUCCESS, note the produced `target/Habbo-*.jar`.
- [ ] **Step 5: Commit** `git commit -am "feat(furni-editor): upsert — create furnidata entry when classname missing (10046)"`

### Task 5: Client — unlock + relabel + re-fetch

**Files:** Modify `ui/src/components/furni-editor/views/FurniEditorEditView.tsx`.

- [ ] **Step 1:** Change the `furnidataEditable` memo (line ~240) so a `null` entry no longer hard-locks: when `furniDataEntry === null`, return `true` (editable → will create). Keep the existing classname-mismatch lock for the present-but-mismatched case.
- [ ] **Step 2:** Replace the warning block (lines ~401-405) with an informational note when `furniDataEntry === null`: "No furnidata entry yet — saving will create one." Prefill the name input from `item.publicName` when entry is null and the field is empty.
- [ ] **Step 3:** Relabel the Save button to "Create entry" when `furniDataEntry === null`, else "Save name/desc".
- [ ] **Step 4:** On `FurniEditorResultEvent` success, re-send `FurniEditorDetailComposer(item.id)` so `furniDataEntry` repopulates (verify the success handler already refetches; if not, add it).
- [ ] **Step 5:** `yarn --cwd E:/Users/simol/Desktop/DEV/ui typecheck` → clean. **Commit** on a client branch (NOT mixed with PR #236): `git checkout -b feat/furni-editor-create-missing origin/Dev` first, cherry-pick this file's change, commit `feat(furni-editor): create furnidata entry when missing (upsert Save)`.

### Task 6: Runtime verification (Chrome handle)

- [ ] **Step 1:** Restart the emulator with the new jar (the user runs it / `emulatore.bat`). Reload `localhost:5173`.
- [ ] **Step 2:** Open Furni Editor on a furni with NO furnidata entry (the "DB fallback" case). Confirm name/desc now editable + button reads "Create entry".
- [ ] **Step 3:** Type a name, Save. Expect: success result; console shows 10046 sent + 10047 (FurnitureDataReload) broadcast; the furni's name updates live; reopening the editor shows the entry now present (editable normally).
- [ ] **Step 4:** Verify on disk: the new object appears in `Nitro-Files/nitro-assets/gamedata/FurnitureData.json` under the right section, with the mapped fields, and `FurnidataReader` parses the file (no corruption; a `.bak` was made).

---

## Self-review notes
- Spec coverage: upsert trigger (T4/T5), complete entry from items_base (T3), config tier (T4), id=sprite + collision guard (T2/T4), no renderer change (none here), error cases (T2 CreateResult + T4 mapping), tests (T2/T3 unit + T6 runtime). Covered.
- Field map exact column names are locked in Task 1 before any code consumes them (not a placeholder — an explicit investigation task).
- Config key aligned to existing prefix: `items.furnidata.create_tier`.
