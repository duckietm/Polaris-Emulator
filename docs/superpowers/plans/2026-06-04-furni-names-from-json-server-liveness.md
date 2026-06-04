# Furni Names from JSON — Liveness (Piece 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When the furnidata file changes, push only the changed names to every connected client so catalog/inventory/infostand update live, without a giant re-download and without a reconnect.

**Architecture:** The emulator (building on Piece 1) computes a **delta** during reindex, a single-threaded **file watcher** (debounced + throttled) broadcasts a new `FurnitureDataReload` packet (delta, or a compact reload-hint above a cap). The renderer parses it, patches `FurnitureData` + the localization keys, and dispatches the existing `nitro-localization-updated` window event — so the three already-subscribed client surfaces refresh. **No Nitro-V3 client code changes.**

**Tech Stack:** Java (Arcturus, Gson, java.nio WatchService) + TypeScript (Nitro_Render_V3, Vitest).

**Depends on:** Piece 1 plan (`FurnitureTextProvider`, `FurnidataReader`, `FurnidataEntry`, `Item.getDisplayName()`) merged on the same branch.

**Spec:** `docs/superpowers/specs/2026-06-04-furni-names-from-json-server-design.md` (§5, §7, §8).

**Repos / branch:** `feat/furni-names-from-json-server` in both `Arcturus-Morningstar-Extended` and `Nitro_Render_V3`.

**Wire contract — packet `FurnitureDataReload` (server → client), header `10047`:**
```
int   mode                 // 0 = delta, 1 = reload-hint
// mode == 0 (delta):
int   count                // server-capped; client clamps on read
count × { string type ("S"|"I"); int id; string classname; string name; string description }
// mode == 1: no further fields
```
`10046` is taken (editor `FURNI_DATA_UPDATED`); `10047` is the new id. Task R1 Step 2 verifies it is free.

---

## Part A — Renderer (Nitro_Render_V3)

All paths relative to `Nitro_Render_V3/`. Build/test: `yarn build`, `yarn test`.

### Task R1: IncomingHeader + parser + event

**Files:**
- Modify: `packages/communication/src/messages/incoming/IncomingHeader.ts:498`
- Create: `packages/communication/src/messages/parser/furniture/FurnitureDataReloadParser.ts`
- Create: `packages/communication/src/messages/incoming/furniture/FurnitureDataReloadEvent.ts`

- [ ] **Step 1: Add the incoming header constant**

In `packages/communication/src/messages/incoming/IncomingHeader.ts`, right after line 498 (`public static FURNI_DATA_UPDATED = 10046;`), add:

```ts
	public static FURNITURE_DATA_RELOAD = 10047;
```

- [ ] **Step 2: Verify the id is free**

Run: `grep -rn "10047" packages/communication/src/messages/incoming/IncomingHeader.ts packages/communication/src/messages/outgoing/OutgoingHeader.ts`
Expected: only the line you just added. If `10047` already exists, use the next free integer and keep it identical to the Arcturus side (Task A1).

- [ ] **Step 3: Create the parser**

Create `packages/communication/src/messages/parser/furniture/FurnitureDataReloadParser.ts`:

```ts
import { IMessageDataWrapper, IMessageParser } from '@nitrots/api';

export interface FurnidataDeltaEntry
{
    type: string;        // "S" floor | "I" wall
    id: number;
    classname: string;
    name: string;
    description: string;
}

export class FurnitureDataReloadParser implements IMessageParser
{
    private static readonly MAX_ENTRIES = 100000;

    private _mode: number;
    private _entries: FurnidataDeltaEntry[];

    public flush(): boolean
    {
        this._mode = 0;
        this._entries = [];
        return true;
    }

    public parse(wrapper: IMessageDataWrapper): boolean
    {
        if(!wrapper) return false;

        this._mode = wrapper.readInt();
        this._entries = [];

        if(this._mode === 0)
        {
            let count = wrapper.readInt();
            if(count < 0) count = 0;
            if(count > FurnitureDataReloadParser.MAX_ENTRIES) count = FurnitureDataReloadParser.MAX_ENTRIES;

            for(let i = 0; i < count; i++)
            {
                this._entries.push({
                    type: wrapper.readString(),
                    id: wrapper.readInt(),
                    classname: wrapper.readString(),
                    name: wrapper.readString(),
                    description: wrapper.readString()
                });
            }
        }

        return true;
    }

    public get mode(): number { return this._mode; }
    public get entries(): FurnidataDeltaEntry[] { return this._entries; }
}
```

- [ ] **Step 4: Create the event**

Create `packages/communication/src/messages/incoming/furniture/FurnitureDataReloadEvent.ts`:

```ts
import { IMessageEvent } from '@nitrots/api';
import { MessageEvent } from '@nitrots/events';
import { FurnitureDataReloadParser } from '../../parser/furniture/FurnitureDataReloadParser';

export class FurnitureDataReloadEvent extends MessageEvent implements IMessageEvent
{
    constructor(callBack: Function)
    {
        super(callBack, FurnitureDataReloadParser);
    }

    public getParser(): FurnitureDataReloadParser
    {
        return this.parser as FurnitureDataReloadParser;
    }
}
```

- [ ] **Step 5: Export both from the barrels**

In `packages/communication/src/messages/parser/furniture/index.ts` add:

```ts
export * from './FurnitureDataReloadParser';
```

In `packages/communication/src/messages/incoming/furniture/index.ts` add:

```ts
export * from './FurnitureDataReloadEvent';
```

(If either `furniture/index.ts` does not exist, create it with the single `export * from './FurnitureDataReloadParser';` / `...Event` line and add `export * from './furniture';` to the parent `incoming/index.ts` and `parser/index.ts`.)

- [ ] **Step 6: Compile**

Run: `yarn compile:fast`
Expected: no errors.

- [ ] **Step 7: Commit**

```bash
git add packages/communication/src/messages/incoming/IncomingHeader.ts packages/communication/src/messages/parser/furniture/ packages/communication/src/messages/incoming/furniture/
git commit -m "feat(communication): FurnitureDataReload incoming event + parser (header 10047)"
```

---

### Task R2: Map the header → event in NitroMessages

**Files:**
- Modify: `packages/communication/src/NitroMessages.ts:91`

- [ ] **Step 1: Register the event class**

In `packages/communication/src/NitroMessages.ts`, right after line 91 (`this._events.set(IncomingHeader.FURNI_DATA_UPDATED, FurniDataUpdatedEvent);`), add:

```ts
		this._events.set(IncomingHeader.FURNITURE_DATA_RELOAD, FurnitureDataReloadEvent);
```

Ensure `FurnitureDataReloadEvent` is imported at the top of the file (add it to the existing incoming-events import block, e.g. `import { ..., FurnitureDataReloadEvent } from './messages';` matching how `FurniDataUpdatedEvent` is imported there).

- [ ] **Step 2: Compile**

Run: `yarn compile:fast`
Expected: no errors (the symbol resolves through the barrels from Task R1 Step 5).

- [ ] **Step 3: Commit**

```bash
git add packages/communication/src/NitroMessages.ts
git commit -m "feat(communication): route FURNITURE_DATA_RELOAD to its event"
```

---

### Task R3: `applyFurnidataDelta` + reload-hint + register handler (TDD)

**Files:**
- Modify: `packages/session/src/SessionDataManager.ts`
- Test: `packages/session/src/__tests__/SessionDataManager.furnidataDelta.test.ts`

- [ ] **Step 1: Write the failing test**

Create `packages/session/src/__tests__/SessionDataManager.furnidataDelta.test.ts`:

```ts
import { describe, expect, it, vi, beforeEach } from 'vitest';

// Minimal localization + window doubles
const setValue = vi.fn();
vi.mock('@nitrots/localization', () => ({
    GetLocalizationManager: () => ({ setValue })
}));
vi.mock('@nitrots/events', async (orig) => {
    const actual = await orig() as any;
    return { ...actual, GetEventDispatcher: () => ({ dispatchEvent: vi.fn() }) };
});

import { applyFurnidataDeltaTo } from '../furniture/applyFurnidataDelta';

describe('applyFurnidataDelta', () => {
    beforeEach(() => { setValue.mockClear(); });

    it('patches floor FurnitureData name/description and localization keys, dispatches window event', () => {
        const floor: any = { _localizedName: 'Old', _description: 'Old desc' };
        const floorItems = new Map<number, any>([[ 5, floor ]]);
        const wallItems = new Map<number, any>();
        const dispatched: string[] = [];
        const win: any = { dispatchEvent: (e: any) => dispatched.push(e.type) };

        applyFurnidataDeltaTo(
            [ { type: 'S', id: 5, classname: 'chair', name: 'New', description: 'New desc' } ],
            floorItems, wallItems, { setValue } as any, win
        );

        expect(floor._localizedName).toBe('New');
        expect(floor._description).toBe('New desc');
        expect(setValue).toHaveBeenCalledWith('roomItem.name.5', 'New');
        expect(setValue).toHaveBeenCalledWith('roomItem.desc.5', 'New desc');
        expect(dispatched).toContain('nitro-localization-updated');
    });

    it('patches wall items by id', () => {
        const wall: any = { _localizedName: 'W', _description: '' };
        const wallItems = new Map<number, any>([[ 9, wall ]]);
        applyFurnidataDeltaTo(
            [ { type: 'I', id: 9, classname: 'poster', name: 'WallNew', description: 'd' } ],
            new Map(), wallItems, { setValue } as any, { dispatchEvent: () => {} } as any
        );
        expect(wall._localizedName).toBe('WallNew');
        expect(setValue).toHaveBeenCalledWith('wallItem.name.9', 'WallNew');
    });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `yarn test SessionDataManager.furnidataDelta`
Expected: FAIL — `../furniture/applyFurnidataDelta` does not exist.

- [ ] **Step 3: Extract the pure patch function**

Create `packages/session/src/furniture/applyFurnidataDelta.ts`:

```ts
import { ILocalizationManager } from '@nitrots/api';
import { FurnidataDeltaEntry } from '@nitrots/communication';

/**
 * Pure, testable furnidata-delta patcher. Mutates the FurnitureData objects in
 * the given maps (by id) and the localization keys, then dispatches the
 * `nitro-localization-updated` window event so subscribed React surfaces refresh.
 */
export function applyFurnidataDeltaTo(
    entries: FurnidataDeltaEntry[],
    floorItems: Map<number, any>,
    wallItems: Map<number, any>,
    localization: Pick<ILocalizationManager, 'setValue'>,
    win: { dispatchEvent: (event: Event) => void }
): void
{
    if(!entries || !entries.length) return;

    for(const e of entries)
    {
        if(e.type === 'I')
        {
            const wall = wallItems.get(e.id);
            if(wall) { wall._localizedName = e.name; wall._description = e.description; }
            localization.setValue('wallItem.name.' + e.id, e.name);
            localization.setValue('wallItem.desc.' + e.id, e.description);
        }
        else
        {
            const floor = floorItems.get(e.id);
            if(floor) { floor._localizedName = e.name; floor._description = e.description; }
            localization.setValue('roomItem.name.' + e.id, e.name);
            localization.setValue('roomItem.desc.' + e.id, e.description);
        }
    }

    if(win && typeof win.dispatchEvent === 'function')
    {
        win.dispatchEvent(new CustomEvent('nitro-localization-updated'));
    }
}
```

If `FurnidataDeltaEntry` is not yet re-exported from `@nitrots/communication`, add `export * from './messages/parser/furniture/FurnitureDataReloadParser';` to `packages/communication/src/index.ts` (or the nearest public barrel) so the type is importable.

- [ ] **Step 4: Run to verify it passes**

Run: `yarn test SessionDataManager.furnidataDelta`
Expected: PASS — 2 tests.

- [ ] **Step 5: Wire the methods + handler into SessionDataManager**

In `packages/session/src/SessionDataManager.ts`, add the import near the other furniture imports:

```ts
import { applyFurnidataDeltaTo } from './furniture/applyFurnidataDelta';
import { FurnidataDeltaEntry, FurnitureDataReloadEvent } from '@nitrots/communication';
```

Add these methods just after `applyLiveFurnitureNameUpdate` (after line 115):

```ts
    public applyFurnidataDelta(entries: FurnidataDeltaEntry[]): void
    {
        applyFurnidataDeltaTo(entries, this._floorItems as any, this._wallItems as any, GetLocalizationManager(), (typeof window !== 'undefined') ? window : { dispatchEvent: () => {} } as any);
    }

    public async applyFurnidataReloadHint(): Promise<void>
    {
        await this._furnitureData.init();
        if(typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('nitro-localization-updated'));
    }
```

In `init()`, add a registration to the `this._messageEvents.push(...)` list (after the `FurniDataUpdatedEvent` registration at line 208-212):

```ts
            GetCommunication().registerMessageEvent(new FurnitureDataReloadEvent((event: FurnitureDataReloadEvent) =>
            {
                const parser = event.getParser();
                if(parser.mode === 1) { void this.applyFurnidataReloadHint(); }
                else { this.applyFurnidataDelta(parser.entries); }
            }))
```

(Add a comma after the previous entry as needed so the push args stay comma-separated.)

- [ ] **Step 6: Compile + run the full session test suite**

Run: `yarn compile:fast && yarn test packages/session`
Expected: no compile errors; all session tests pass including the new ones.

- [ ] **Step 7: Commit**

```bash
git add packages/session/src/furniture/applyFurnidataDelta.ts packages/session/src/SessionDataManager.ts packages/session/src/__tests__/SessionDataManager.furnidataDelta.test.ts packages/communication/src/index.ts
git commit -m "feat(session): apply FurnitureDataReload delta + reload-hint, separate from editor path"
```

---

## Part B — Emulator (Arcturus-Morningstar-Extended)

All paths relative to `Arcturus-Morningstar-Extended/Emulator/`.

### Task A1: Outgoing header + `FurnitureDataReloadComposer`

**Files:**
- Modify: `src/main/java/com/eu/habbo/messages/outgoing/Outgoing.java`
- Create: `src/main/java/com/eu/habbo/messages/outgoing/furniture/FurnitureDataReloadComposer.java`

- [ ] **Step 1: Add the Outgoing header constant**

In `src/main/java/com/eu/habbo/messages/outgoing/Outgoing.java`, add (matching the file's existing `public static final int Name = id;` style):

```java
    public static final int FurnitureDataReloadComposer = 10047;
```

- [ ] **Step 2: Verify the id is free on the server side**

Run: `grep -rn "= 10047" src/main/java/com/eu/habbo/messages/outgoing/Outgoing.java`
Expected: only the line just added. If taken, pick the next free id and keep it equal to the renderer `IncomingHeader.FURNITURE_DATA_RELOAD` (Task R1).

- [ ] **Step 3: Create the composer**

Create `src/main/java/com/eu/habbo/messages/outgoing/furniture/FurnitureDataReloadComposer.java`:

```java
package com.eu.habbo.messages.outgoing.furniture;

import com.eu.habbo.habbohotel.items.FurnidataEntry;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public class FurnitureDataReloadComposer extends MessageComposer {

    public static final int MODE_DELTA = 0;
    public static final int MODE_RELOAD_HINT = 1;

    private final int mode;
    private final List<FurnidataEntry> entries;

    public FurnitureDataReloadComposer(int mode, List<FurnidataEntry> entries) {
        this.mode = mode;
        this.entries = entries;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FurnitureDataReloadComposer);
        this.response.appendInt(this.mode);

        if (this.mode == MODE_DELTA) {
            this.response.appendInt(this.entries.size());
            for (FurnidataEntry e : this.entries) {
                this.response.appendString(e.type() == FurnitureType.FLOOR ? "S" : "I");
                this.response.appendInt(e.id());
                this.response.appendString(e.classname());
                this.response.appendString(e.name());
                this.response.appendString(e.description());
            }
        }

        return this.response;
    }
}
```

- [ ] **Step 4: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/eu/habbo/messages/outgoing/Outgoing.java src/main/java/com/eu/habbo/messages/outgoing/furniture/FurnitureDataReloadComposer.java
git commit -m "feat(items): FurnitureDataReloadComposer (header 10047, delta + reload-hint)"
```

---

### Task A2: `FurnitureTextProvider.reindex` returns a sanitized delta (TDD)

Piece 1 defined `reindex(List)` returning `void`. Change it to return the changed entries (sanitized), so the watcher can broadcast them. Existing call sites ignore the return value — no other change needed.

**Files:**
- Modify: `src/main/java/com/eu/habbo/habbohotel/items/FurnitureTextProvider.java`
- Test: `src/test/java/com/eu/habbo/habbohotel/items/FurnitureTextProviderDeltaTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/eu/habbo/habbohotel/items/FurnitureTextProviderDeltaTest.java`:

```java
package com.eu.habbo.habbohotel.items;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FurnitureTextProviderDeltaTest {

    @Test
    void firstReindexReturnsAllAsDelta() {
        FurnitureTextProvider p = new FurnitureTextProvider(true);
        List<FurnidataEntry> delta = p.reindex(List.of(
            new FurnidataEntry(1, "chair", FurnitureType.FLOOR, "Chair", "Sit")));
        assertEquals(1, delta.size());
        assertEquals("Chair", delta.get(0).name());
    }

    @Test
    void unchangedReindexReturnsEmptyDelta() {
        FurnitureTextProvider p = new FurnitureTextProvider(true);
        List<FurnidataEntry> first = List.of(new FurnidataEntry(1, "chair", FurnitureType.FLOOR, "Chair", "Sit"));
        p.reindex(first);
        List<FurnidataEntry> delta = p.reindex(first);
        assertTrue(delta.isEmpty(), "no change => empty delta");
    }

    @Test
    void changedNameAppearsInDeltaWithSanitizedValue() {
        FurnitureTextProvider p = new FurnitureTextProvider(true);
        p.reindex(List.of(new FurnidataEntry(1, "chair", FurnitureType.FLOOR, "Chair", "Sit")));
        List<FurnidataEntry> delta = p.reindex(List.of(
            new FurnidataEntry(1, "chair", FurnitureType.FLOOR, "New %x%", "Sit")));
        assertEquals(1, delta.size());
        assertFalse(delta.get(0).name().contains("%"), "delta carries the sanitized name");
        assertEquals(1, delta.get(0).id());
        assertEquals(FurnitureType.FLOOR, delta.get(0).type());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -Dtest=FurnitureTextProviderDeltaTest`
Expected: FAIL — `reindex` returns `void` (compile error) / method shape mismatch.

- [ ] **Step 3: Change `reindex` to compute and return the delta**

In `FurnitureTextProvider.java`, replace the existing `reindex` method with:

```java
    /**
     * Build a fresh sanitized index, swap it in atomically, and return the
     * changed/added entries (sanitized) as the delta versus the previous index.
     */
    public java.util.List<FurnidataEntry> reindex(java.util.List<FurnidataEntry> entries) {
        Map<String, FurniText> next = new HashMap<>(Math.max(16, entries.size() * 2));
        for (FurnidataEntry e : entries) {
            String key = baseKey(e.classname());
            if (key == null) continue;
            next.put(key, new FurniText(e.id(), e.type(), sanitize(e.name()), sanitize(e.description())));
        }

        Map<String, FurniText> prev = this.index;
        java.util.List<FurnidataEntry> delta = new java.util.ArrayList<>();
        for (Map.Entry<String, FurniText> en : next.entrySet()) {
            FurniText cur = en.getValue();
            FurniText old = prev.get(en.getKey());
            if (old == null || !old.name().equals(cur.name()) || !old.description().equals(cur.description())) {
                delta.add(new FurnidataEntry(cur.id(), en.getKey(), cur.type(), cur.name(), cur.description()));
            }
        }

        this.index = next; // atomic reference swap
        return delta;
    }
```

- [ ] **Step 4: Run both provider tests**

Run: `mvn -q test -Dtest=FurnitureTextProviderTest,FurnitureTextProviderDeltaTest`
Expected: PASS — Piece-1 provider tests (6) still pass (they ignore the return value), delta tests (3) pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/eu/habbo/habbohotel/items/FurnitureTextProvider.java src/test/java/com/eu/habbo/habbohotel/items/FurnitureTextProviderDeltaTest.java
git commit -m "feat(items): reindex returns sanitized furnidata delta"
```

---

### Task A3: File watcher — debounce, throttle, cap→hint, broadcast

**Files:**
- Create: `src/main/java/com/eu/habbo/habbohotel/items/FurnidataWatcher.java`
- Modify: `src/main/java/com/eu/habbo/habbohotel/items/FurnitureTextProvider.java` (expose source path + start watcher)

- [ ] **Step 1: Expose the resolved source and start the watcher from `init()`**

In `FurnitureTextProvider.java`, store the resolved source and start the watcher at the end of `init()`. Replace the body of `init()` with:

```java
    private volatile Path source;
    private FurnidataWatcher watcher;

    public void init() {
        try {
            this.source = resolveSource();
            if (this.source == null) {
                LOGGER.warn("FurnitureTextProvider: no furnidata source resolved — names fall back to public_name");
                return;
            }
            reindex(new FurnidataReader(this.source, DEFAULT_MAX_BYTES).read());
            LOGGER.info("FurnitureTextProvider: indexed {} furnidata names from {}", this.index.size(), this.source);

            if (Boolean.parseBoolean(Emulator.getConfig().getValue("items.furnidata.watch.enabled", "true"))) {
                this.watcher = new FurnidataWatcher(this, this.source, DEFAULT_MAX_BYTES);
                this.watcher.start();
            }
        } catch (Exception e) {
            LOGGER.warn("FurnitureTextProvider.init failed — names fall back to public_name", e);
        }
    }

    public Path getSource() {
        return this.source;
    }
```

(Add `import java.nio.file.Path;` if not already present from Piece 1 Task 4.)

- [ ] **Step 2: Create the watcher**

Create `src/main/java/com/eu/habbo/habbohotel/items/FurnidataWatcher.java`:

```java
package com.eu.habbo.habbohotel.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.furniture.FurnitureDataReloadComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;

/**
 * Watches the furnidata source on a single daemon thread. On change (debounced),
 * re-indexes via the provider and broadcasts only the delta — or a compact
 * reload-hint when the delta exceeds the cap. A minimum interval throttles bursts.
 * Never throws out of the loop.
 */
public class FurnidataWatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(FurnidataWatcher.class);

    private final FurnitureTextProvider provider;
    private final Path watchDir;
    private final long maxBytes;
    private final long debounceMs;
    private final long minIntervalMs;
    private final int deltaCap;

    private volatile boolean running = false;
    private long lastBroadcast = 0L;

    public FurnidataWatcher(FurnitureTextProvider provider, Path source, long maxBytes) {
        this.provider = provider;
        // Watch the parent dir for a file, or the dir itself for a split layout.
        this.watchDir = java.nio.file.Files.isDirectory(source) ? source : source.getParent();
        this.maxBytes = maxBytes;
        this.debounceMs = Long.parseLong(Emulator.getConfig().getValue("items.furnidata.watch.debounce.ms", "750"));
        this.minIntervalMs = Long.parseLong(Emulator.getConfig().getValue("items.furnidata.watch.min.interval.ms", "5000"));
        this.deltaCap = Integer.parseInt(Emulator.getConfig().getValue("items.furnidata.delta.cap", "500"));
    }

    public void start() {
        if (this.running || this.watchDir == null) return;
        this.running = true;
        Thread t = new Thread(this::run, "FurnidataWatcher");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        this.running = false;
    }

    private void run() {
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            this.watchDir.register(ws, StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);

            while (this.running) {
                WatchKey key = ws.take();              // blocks
                key.pollEvents();                       // drain
                Thread.sleep(this.debounceMs);          // debounce burst writes
                key.pollEvents();                       // drain anything that arrived during debounce
                key.reset();

                try {
                    onChange();
                } catch (Exception e) {
                    LOGGER.warn("FurnidataWatcher: onChange failed", e);
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.warn("FurnidataWatcher stopped", e);
        }
    }

    private void onChange() {
        Path source = this.provider.getSource();
        if (source == null) return;

        List<FurnidataEntry> delta = this.provider.reindex(new FurnidataReader(source, this.maxBytes).read());
        if (delta.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - this.lastBroadcast < this.minIntervalMs) {
            LOGGER.info("FurnidataWatcher: {} changes throttled (min interval)", delta.size());
            return; // next change will pick up the new baseline
        }
        this.lastBroadcast = now;

        FurnitureDataReloadComposer composer = (delta.size() > this.deltaCap)
            ? new FurnitureDataReloadComposer(FurnitureDataReloadComposer.MODE_RELOAD_HINT, List.of())
            : new FurnitureDataReloadComposer(FurnitureDataReloadComposer.MODE_DELTA, delta);

        broadcast(composer);
        LOGGER.info("FurnidataWatcher: broadcast {} ({} entries)",
            delta.size() > this.deltaCap ? "reload-hint" : "delta", delta.size());
    }

    private void broadcast(FurnitureDataReloadComposer composer) {
        for (Habbo habbo : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {
            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(composer);
            }
        }
    }
}
```

`System.currentTimeMillis()` is fine here (production server runtime). Note: when throttled, the index is still updated but the delta is **not** sent, and it is not re-sent later — those names update on client reconnect (acceptable for infrequent admin edits). To never drop, raise `items.furnidata.delta.cap` is unrelated; instead lower `items.furnidata.watch.min.interval.ms`.

- [ ] **Step 3: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/eu/habbo/habbohotel/items/FurnidataWatcher.java src/main/java/com/eu/habbo/habbohotel/items/FurnitureTextProvider.java
git commit -m "feat(items): furnidata file watcher — debounce, throttle, delta cap to reload-hint, broadcast"
```

---

## Part C — Integration & acceptance

### Task C1: Build both repos + manual live test

**Files:** none (verification only)

- [ ] **Step 1: Renderer build + tests**

Run (in `Nitro_Render_V3/`): `yarn compile:fast && yarn test`
Expected: no compile errors; all tests pass including `SessionDataManager.furnidataDelta`.

- [ ] **Step 2: Emulator build + tests**

Run (in `Arcturus-Morningstar-Extended/Emulator/`): `mvn -q clean package`
Expected: BUILD SUCCESS; all `FurnitureTextProvider*Test` + `FurnidataReaderTest` pass.

- [ ] **Step 3: Manual live acceptance (running hotel + client)**

1. Start MariaDB, the emulator (with Piece 1 + Piece 2 jar), and the client; enter a room containing a furni whose furnidata `name` you will change.
2. Edit that furni's `name` in the furnidata file and save.
3. Within the debounce window (~1s), confirm WITHOUT refreshing the client:
   - the furni infostand shows the new name (open the furni info),
   - the catalog offer for it shows the new name,
   - the inventory tile label shows the new name.
4. Check the emulator log: `FurnidataWatcher: broadcast delta (1 entries)`.
5. Replace the whole furnidata (mass change) → log shows `broadcast reload-hint`; the client re-loads furnidata and names update.
6. Set `items.furnidata.watch.enabled=false`, restart → editing the file no longer pushes; clients update only on reconnect (watcher disabled).

- [ ] **Step 4: Final no-op commit (optional config docs)**

```bash
git commit --allow-empty -m "docs(items): document items.furnidata.watch.* config keys"
```

---

## Notes for the implementer

- **Header id must match** on both sides: `IncomingHeader.FURNITURE_DATA_RELOAD` (renderer) == `Outgoing.FurnitureDataReloadComposer` (Arcturus) == `10047`. Verify both Step-2 grep checks before wiring.
- **Do not reuse or modify** the editor path: `FurniDataUpdatedEvent`/`FurniDataUpdatedParser`/`applyLiveFurnitureNameUpdate` stay untouched; the new path is parallel (per the spec decision "keep separate").
- **No Nitro-V3 client changes.** The three surfaces (`useCatalog.ts:919`, `useInventoryFurni.ts:137`, `useAvatarInfoWidget.ts:425`) already subscribe to `nitro-localization-updated`. If a regression test is wanted, add it under Nitro-V3 separately; it is not required for this plan.
- **Locale no-clobber (spec §7.1):** re-registering `roomItem.name.{id}` from the base furnidata will override an active per-locale text override for that id. If this hotel ships per-locale furni override files, follow up by re-applying overrides after the delta (out of scope here; single-furnidata hotels are unaffected).
- **Cache-busting** for the reload-hint: `applyFurnidataReloadHint` re-runs `FurnitureDataLoader.init()` against the configured `furnidata.url`; if the asset host serves a cached copy, add a `?v=<timestamp>` buster to the loader fetch (optional follow-up).
