# Furni Names from JSON — Emulator (Piece 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Arcturus emulator source furni display names from the furnidata JSON (keyed by classname) instead of the DB `public_name`, with a sanitized, fail-safe, hot-swappable in-memory index.

**Architecture:** A neutral `FurnidataReader` locates and parses the furnidata (single-file or split-tier JSON5) into `FurnidataEntry` records. `FurnitureTextProvider` builds a `volatile` index `baseClassname → name`, sanitizing at the boundary, and is queried lazily by a new `Item.getDisplayName()` (fallback to DB `public_name`). The 6 server-side sites that pronounce a furni name switch to it. No DB migration, no new packet, no client/renderer change — this Piece ships and is testable on its own.

**Tech Stack:** Java 19/21 (Maven), Gson (already a dependency), JUnit 5 (added in Task 0).

**Spec:** `docs/superpowers/specs/2026-06-04-furni-names-from-json-server-design.md` (§4, §7, §8).

**Repo / branch:** `Arcturus-Morningstar-Extended`, branch `feat/furni-names-from-json-server` (already created, based on `origin/dev`). All paths below are relative to `Arcturus-Morningstar-Extended/Emulator/`.

---

## File structure

| Action | Path | Responsibility |
|---|---|---|
| Modify | `pom.xml` | add JUnit 5 + surefire (test infra does not exist yet) |
| Create | `src/main/java/com/eu/habbo/habbohotel/items/FurnidataEntry.java` | immutable parsed entry |
| Create | `src/main/java/com/eu/habbo/habbohotel/items/FurnidataReader.java` | locate + parse furnidata (single + split JSON5, path-guard, size-cap, fail-safe) |
| Create | `src/main/java/com/eu/habbo/habbohotel/items/FurnitureTextProvider.java` | volatile index, sanitize, toggle, `getName()`, `reindex()` |
| Modify | `src/main/java/com/eu/habbo/habbohotel/items/Item.java` | add `getDisplayName()` |
| Modify | `src/main/java/com/eu/habbo/habbohotel/GameEnvironment.java` | construct + init provider, add getter |
| Modify | `src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java:1057,1063` | `getFullName()` → `getDisplayName()` |
| Modify | `src/main/java/com/eu/habbo/messages/incoming/catalog/CatalogBuyItemAsGiftEvent.java:251,262` | `getFullName()` → `getDisplayName()` |
| Modify | `src/main/java/com/eu/habbo/habbohotel/wired/core/WiredTextPlaceholderUtil.java:282` | `getFullName()` → `getDisplayName()` |
| Modify | `src/main/java/com/eu/habbo/messages/outgoing/unknown/WatchAndEarnRewardComposer.java:21` | `getFullName()` → `getDisplayName()` |
| Create | `src/test/java/com/eu/habbo/habbohotel/items/FurnidataReaderTest.java` | parse + path-guard + size-cap |
| Create | `src/test/java/com/eu/habbo/habbohotel/items/FurnitureTextProviderTest.java` | index, color-variant, fallback, sanitize, toggle |

---

## Task 0: Add JUnit 5 test infrastructure

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add the JUnit Jupiter dependency**

In `pom.xml`, inside the existing `<dependencies>` element, add:

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.2</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Add the Surefire plugin**

In `pom.xml`, inside `<build><plugins>`, add:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
</plugin>
```

- [ ] **Step 3: Create a smoke test to prove the harness runs**

Create `src/test/java/com/eu/habbo/SmokeTest.java`:

```java
package com.eu.habbo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmokeTest {
    @Test
    void harnessRuns() {
        assertTrue(true);
    }
}
```

- [ ] **Step 4: Run the test suite**

Run: `mvn -q test -Dtest=SmokeTest`
Expected: BUILD SUCCESS, 1 test run, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/test/java/com/eu/habbo/SmokeTest.java
git commit -m "test: add JUnit 5 + surefire harness"
```

---

## Task 1: `FurnidataEntry` record

**Files:**
- Create: `src/main/java/com/eu/habbo/habbohotel/items/FurnidataEntry.java`

- [ ] **Step 1: Create the record**

```java
package com.eu.habbo.habbohotel.items;

/**
 * One parsed furnidata entry. {@code classname} is the raw furnidata classname
 * (may carry a {@code *N} colour-variant suffix); the provider keys on the base.
 */
public record FurnidataEntry(int id, String classname, FurnitureType type, String name, String description) {
}
```

- [ ] **Step 2: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/eu/habbo/habbohotel/items/FurnidataEntry.java
git commit -m "feat(items): FurnidataEntry record"
```

---

## Task 2: `FurnidataReader` — locate & parse (TDD)

**Files:**
- Create: `src/main/java/com/eu/habbo/habbohotel/items/FurnidataReader.java`
- Test: `src/test/java/com/eu/habbo/habbohotel/items/FurnidataReaderTest.java`

The reader is decoupled from `Emulator` config so it is unit-testable: it takes an explicit base `Path` and a max-bytes cap. (Config resolution is wired in Task 4.)

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/eu/habbo/habbohotel/items/FurnidataReaderTest.java`:

```java
package com.eu.habbo.habbohotel.items;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FurnidataReaderTest {

    private static final String SINGLE = """
        {
          // a comment
          "roomitemtypes": { "furnitype": [
            { "id": 10, "classname": "chair_norja", "name": "Chair", "description": "Sit", "xdim": 1, "ydim": 1 },
          ]},
          "wallitemtypes": { "furnitype": [
            { "id": 20, "classname": "poster_5", "name": "Poster", "description": "Wall" }
          ]}
        }
        """;

    @Test
    void parsesSingleFileFloorAndWall(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("FurnitureData.json");
        Files.writeString(file, SINGLE);

        List<FurnidataEntry> entries = new FurnidataReader(file, 64 * 1024 * 1024).read();

        assertEquals(2, entries.size());
        FurnidataEntry floor = entries.stream().filter(e -> e.id() == 10).findFirst().orElseThrow();
        assertEquals("chair_norja", floor.classname());
        assertEquals(FurnitureType.FLOOR, floor.type());
        assertEquals("Chair", floor.name());
        FurnidataEntry wall = entries.stream().filter(e -> e.id() == 20).findFirst().orElseThrow();
        assertEquals(FurnitureType.WALL, wall.type());
    }

    @Test
    void rejectsFileOverSizeCap(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("FurnitureData.json");
        Files.writeString(file, SINGLE);
        List<FurnidataEntry> entries = new FurnidataReader(file, 8 /* bytes */).read();
        assertTrue(entries.isEmpty(), "oversized file must be refused, returning empty");
    }

    @Test
    void missingSourceReturnsEmptyNeverThrows(@TempDir Path dir) {
        Path missing = dir.resolve("does-not-exist.json");
        assertDoesNotThrow(() -> {
            assertTrue(new FurnidataReader(missing, 64 * 1024 * 1024).read().isEmpty());
        });
    }

    @Test
    void splitDirRejectsTraversalFiles(@TempDir Path dir) throws Exception {
        // secret outside the base dir
        Path secret = dir.resolve("secret.json");
        Files.writeString(secret, "{ \"roomitemtypes\": { \"furnitype\": [ { \"id\": 99, \"classname\": \"x\", \"name\": \"LEAK\", \"description\": \"\" } ] } }");

        Path base = dir.resolve("furnidata");
        Path core = base.resolve("core");
        Files.createDirectories(core);
        // tiers manifest
        Files.writeString(base.resolve("manifest.json"), "{ \"tiers\": [ \"core\" ] }");
        // files manifest points OUTSIDE core via traversal
        Files.writeString(core.resolve("manifest.json"), "{ \"files\": [ \"../../secret.json\" ] }");

        List<FurnidataEntry> entries = new FurnidataReader(base, 64 * 1024 * 1024).read();

        assertTrue(entries.stream().noneMatch(e -> e.id() == 99),
            "traversal file outside the base dir must be ignored");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -Dtest=FurnidataReaderTest`
Expected: FAIL — `FurnidataReader` does not exist (compilation error).

- [ ] **Step 3: Implement `FurnidataReader`**

Create `src/main/java/com/eu/habbo/habbohotel/items/FurnidataReader.java`:

```java
package com.eu.habbo.habbohotel.items;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Neutral furnidata reader. Supports a single JSON/JSON5 file or a split-tier
 * directory ({@code core/custom/seasonal} with {@code manifest.json(5)}).
 * Never throws: any IO/parse error yields an empty list (the caller decides the
 * fallback). All resolved paths are guarded against escaping the base dir.
 */
public class FurnidataReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(FurnidataReader.class);
    private static final List<String> DEFAULT_TIERS = Arrays.asList("core", "custom", "seasonal");
    private static final List<String> MANIFEST_NAMES = Arrays.asList("manifest.json5", "manifest.json");
    private static final List<String> SECTIONS = Arrays.asList("roomitemtypes", "wallitemtypes");

    private final Path source;
    private final long maxBytes;

    public FurnidataReader(Path source, long maxBytes) {
        this.source = source;
        this.maxBytes = maxBytes;
    }

    public List<FurnidataEntry> read() {
        List<FurnidataEntry> out = new ArrayList<>();
        try {
            if (this.source == null || !Files.exists(this.source)) return out;

            if (Files.isDirectory(this.source)) {
                readSplitDir(this.source, out);
            } else {
                String content = readJson5Capped(this.source);
                if (content != null) {
                    parseRoot(JsonParser.parseString(content).getAsJsonObject(), out);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("FurnidataReader failed to read {} — returning empty", this.source, e);
            return new ArrayList<>();
        }
        return out;
    }

    private void readSplitDir(Path base, List<FurnidataEntry> out) {
        List<String> tiers = readManifestList(base, "tiers", DEFAULT_TIERS);
        Path baseNorm = base.toAbsolutePath().normalize();

        for (String tier : tiers) {
            Path tierDir = base.resolve(tier);
            if (!isInside(baseNorm, tierDir) || !Files.isDirectory(tierDir)) continue;

            for (String fileName : readManifestList(tierDir, "files", List.of())) {
                Path file = tierDir.resolve(fileName);
                if (!isInside(baseNorm, file)) {
                    LOGGER.warn("FurnidataReader: ignoring out-of-base file {}", file);
                    continue;
                }
                if (!Files.exists(file)) continue;
                try {
                    String content = readJson5Capped(file);
                    if (content != null) parseRoot(JsonParser.parseString(content).getAsJsonObject(), out);
                } catch (Exception e) {
                    LOGGER.warn("FurnidataReader: failed to parse {}", file, e);
                }
            }
        }
    }

    private List<String> readManifestList(Path dir, String key, List<String> fallback) {
        for (String name : MANIFEST_NAMES) {
            Path m = dir.resolve(name);
            if (!Files.exists(m)) continue;
            try {
                JsonObject obj = JsonParser.parseString(readJson5Capped(m)).getAsJsonObject();
                if (obj.has(key) && obj.get(key).isJsonArray()) {
                    List<String> list = new ArrayList<>();
                    for (JsonElement el : obj.getAsJsonArray(key)) list.add(el.getAsString());
                    if (!list.isEmpty()) return list;
                }
            } catch (Exception e) {
                LOGGER.warn("FurnidataReader: bad manifest {}", m, e);
            }
        }
        return fallback;
    }

    private void parseRoot(JsonObject root, List<FurnidataEntry> out) {
        for (String section : SECTIONS) {
            if (!root.has(section)) continue;
            JsonObject sectionObj = root.getAsJsonObject(section);
            if (!sectionObj.has("furnitype")) continue;
            FurnitureType type = section.equals("roomitemtypes") ? FurnitureType.FLOOR : FurnitureType.WALL;
            JsonArray types = sectionObj.getAsJsonArray("furnitype");
            for (JsonElement el : types) {
                JsonObject o = el.getAsJsonObject();
                if (!o.has("id") || !o.has("classname")) continue;
                out.add(new FurnidataEntry(
                    o.get("id").getAsInt(),
                    o.get("classname").getAsString(),
                    type,
                    o.has("name") ? o.get("name").getAsString() : "",
                    o.has("description") ? o.get("description").getAsString() : ""
                ));
            }
        }
    }

    /** Returns the JSON5-stripped content, or null if the file exceeds the byte cap. */
    private String readJson5Capped(Path path) throws Exception {
        long size = Files.size(path);
        if (size > this.maxBytes) {
            LOGGER.warn("FurnidataReader: {} is {} bytes, over cap {} — refusing", path, size, this.maxBytes);
            return null;
        }
        return stripJson5(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static boolean isInside(Path baseNorm, Path candidate) {
        return candidate.toAbsolutePath().normalize().startsWith(baseNorm);
    }

    /** Strip // and block comments and trailing commas so Gson can parse JSON5. */
    static String stripJson5(String content) {
        if (content == null || content.isEmpty()) return content;
        StringBuilder out = new StringBuilder(content.length());
        int i = 0, len = content.length();
        boolean inString = false, escape = false;
        char stringChar = 0;
        while (i < len) {
            char c = content.charAt(i);
            if (inString) {
                out.append(c);
                if (escape) escape = false;
                else if (c == '\\') escape = true;
                else if (c == stringChar) inString = false;
                i++;
                continue;
            }
            if (c == '"' || c == '\'') { inString = true; stringChar = c; out.append(c); i++; continue; }
            if (c == '/' && i + 1 < len) {
                char next = content.charAt(i + 1);
                if (next == '/') { int eol = content.indexOf('\n', i + 2); if (eol < 0) break; i = eol; continue; }
                if (next == '*') { int end = content.indexOf("*/", i + 2); if (end < 0) break; i = end + 2; continue; }
            }
            out.append(c);
            i++;
        }
        return out.toString().replaceAll(",(\\s*[}\\]])", "$1");
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -q test -Dtest=FurnidataReaderTest`
Expected: PASS — 4 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/eu/habbo/habbohotel/items/FurnidataReader.java src/test/java/com/eu/habbo/habbohotel/items/FurnidataReaderTest.java
git commit -m "feat(items): FurnidataReader (single + split JSON5, path-guard, size-cap, fail-safe)"
```

---

## Task 3: `FurnitureTextProvider` — index, sanitize, toggle (TDD)

**Files:**
- Create: `src/main/java/com/eu/habbo/habbohotel/items/FurnitureTextProvider.java`
- Test: `src/test/java/com/eu/habbo/habbohotel/items/FurnitureTextProviderTest.java`

The provider is constructed with an already-built entry list (so it is unit-testable without files); the config-driven loading is wired in Task 4.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/eu/habbo/habbohotel/items/FurnitureTextProviderTest.java`:

```java
package com.eu.habbo.habbohotel.items;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FurnitureTextProviderTest {

    private FurnitureTextProvider provider(boolean enabled, FurnidataEntry... entries) {
        FurnitureTextProvider p = new FurnitureTextProvider(enabled);
        p.reindex(List.of(entries));
        return p;
    }

    @Test
    void resolvesNameByClassname() {
        FurnitureTextProvider p = provider(true,
            new FurnidataEntry(1, "chair_norja", FurnitureType.FLOOR, "Norja Chair", "Sit"));
        assertEquals("Norja Chair", p.getName("chair_norja"));
    }

    @Test
    void matchesBaseClassnameIgnoringColourVariantAndCase() {
        FurnitureTextProvider p = provider(true,
            new FurnidataEntry(1, "chair_norja*2", FurnitureType.FLOOR, "Norja Chair", "Sit"));
        // DB item_name is the base classname; lookup must strip the *N and be case-insensitive
        assertEquals("Norja Chair", p.getName("CHAIR_NORJA"));
    }

    @Test
    void returnsNullWhenClassnameMissing() {
        FurnitureTextProvider p = provider(true);
        assertNull(p.getName("unknown_thing"));
    }

    @Test
    void returnsNullWhenDisabled() {
        FurnitureTextProvider p = provider(false,
            new FurnidataEntry(1, "chair_norja", FurnitureType.FLOOR, "Norja Chair", "Sit"));
        assertNull(p.getName("chair_norja"));
    }

    @Test
    void sanitizesNameCapStripControlAndNeutralizesPercent() {
        String evil = "Bad\nName %limit% %user.name%".repeat(20); // long + control + % tokens
        FurnitureTextProvider p = provider(true,
            new FurnidataEntry(1, "x", FurnitureType.FLOOR, evil, ""));
        String name = p.getName("x");
        assertTrue(name.length() <= 256, "must be capped to 256");
        assertFalse(name.contains(System.lineSeparator()), "newlines stripped");
        assertFalse(name.chars().anyMatch(Character::isISOControl), "no control chars remain after sanitize");
        assertFalse(name.contains(""), "control chars stripped");
        assertFalse(name.contains("%"), "percent neutralized");
    }

    @Test
    void nullProviderNameNeverThrows() {
        FurnitureTextProvider p = provider(true);
        assertDoesNotThrow(() -> p.getName(null));
        assertNull(p.getName(null));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -Dtest=FurnitureTextProviderTest`
Expected: FAIL — `FurnitureTextProvider` does not exist.

- [ ] **Step 3: Implement `FurnitureTextProvider`**

Create `src/main/java/com/eu/habbo/habbohotel/items/FurnitureTextProvider.java`:

```java
package com.eu.habbo.habbohotel.items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory index of furnidata display names, keyed by the lowercased base
 * classname (the {@code *N} colour-variant suffix is stripped). Read lazily by
 * {@link Item#getDisplayName()}. Names are sanitized at index time.
 *
 * Thread-safety: the index is held behind a {@code volatile} reference; readers
 * never block; {@link #reindex(List)} builds a fresh map and swaps it atomically.
 */
public class FurnitureTextProvider {

    private static final int MAX_LEN = 256;

    private final boolean enabled;
    private volatile Map<String, FurniText> index = Map.of();

    public FurnitureTextProvider(boolean enabled) {
        this.enabled = enabled;
    }

    /** Build a fresh sanitized index from the given entries and swap it in atomically. */
    public void reindex(List<FurnidataEntry> entries) {
        Map<String, FurniText> next = new HashMap<>(Math.max(16, entries.size() * 2));
        for (FurnidataEntry e : entries) {
            String key = baseKey(e.classname());
            if (key == null) continue;
            next.put(key, new FurniText(e.id(), e.type(), sanitize(e.name()), sanitize(e.description())));
        }
        this.index = next; // atomic reference swap
    }

    /** Returns the sanitized display name for a DB classname, or null if absent/disabled. */
    public String getName(String classname) {
        if (!this.enabled) return null;
        String key = baseKey(classname);
        if (key == null) return null;
        FurniText t = this.index.get(key);
        return (t != null) ? t.name() : null;
    }

    private static String baseKey(String classname) {
        if (classname == null) return null;
        int star = classname.indexOf('*');
        String base = (star >= 0) ? classname.substring(0, star) : classname;
        base = base.trim().toLowerCase();
        return base.isEmpty() ? null : base;
    }

    /** Cap length, strip control chars/newlines, neutralize % (placeholder-injection safe). */
    static String sanitize(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(Math.min(value.length(), MAX_LEN));
        for (int i = 0; i < value.length() && sb.length() < MAX_LEN; i++) {
            char c = value.charAt(i);
            if (c == '%') { sb.append('％'); continue; } // fullwidth percent — not a placeholder token
            if (c == '\n' || c == '\r' || Character.isISOControl(c)) continue;
            sb.append(c);
        }
        return sb.toString();
    }

    private record FurniText(int id, FurnitureType type, String name, String description) {}
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -q test -Dtest=FurnitureTextProviderTest`
Expected: PASS — 6 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/eu/habbo/habbohotel/items/FurnitureTextProvider.java src/test/java/com/eu/habbo/habbohotel/items/FurnitureTextProviderTest.java
git commit -m "feat(items): FurnitureTextProvider — volatile index, sanitize, toggle"
```

---

## Task 4: Wire config-driven loading into the provider

Adds a config-driven `init()` that resolves the path (reusing the editor's already-configured `furni.editor.asset.base.path`, with an `items.furnidata.path` override) and reads via `FurnidataReader`. Kept separate from the pure index logic so Task 3's unit tests stay file-free.

**Files:**
- Modify: `src/main/java/com/eu/habbo/habbohotel/items/FurnitureTextProvider.java`

- [ ] **Step 1: Add the config-driven init method**

Add the imports at the top of `FurnitureTextProvider.java`:

```java
import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
```

Replace the constructor section with a no-arg constructor that reads the toggle from config, plus a static factory and `init()`:

```java
    private static final Logger LOGGER = LoggerFactory.getLogger(FurnitureTextProvider.class);
    private static final long DEFAULT_MAX_BYTES = 64L * 1024 * 1024;

    // existing fields kept: private final boolean enabled; private volatile Map<String, FurniText> index = Map.of();

    public FurnitureTextProvider(boolean enabled) {
        this.enabled = enabled;
    }

    /** Production constructor: reads the enable toggle from config. */
    public FurnitureTextProvider() {
        this(Boolean.parseBoolean(Emulator.getConfig().getValue("items.furnidata.names.enabled", "true")));
    }

    /** Resolve the furnidata source from config and build the initial index. Never throws. */
    public void init() {
        try {
            Path source = resolveSource();
            if (source == null) {
                LOGGER.warn("FurnitureTextProvider: no furnidata source resolved — names fall back to public_name");
                return;
            }
            reindex(new FurnidataReader(source, DEFAULT_MAX_BYTES).read());
            LOGGER.info("FurnitureTextProvider: indexed {} furnidata names from {}", this.index.size(), source);
        } catch (Exception e) {
            LOGGER.warn("FurnitureTextProvider.init failed — names fall back to public_name", e);
        }
    }

    private static Path resolveSource() {
        String override = Emulator.getConfig().getValue("items.furnidata.path", "");
        if (!override.isEmpty()) {
            Path p = Paths.get(override);
            return Files.exists(p) ? p : null;
        }
        String basePath = Emulator.getConfig().getValue("furni.editor.asset.base.path", "");
        if (basePath.isEmpty()) return null;
        Path dir = Paths.get(basePath);
        Path split = dir.resolve("furnidata");
        if (Files.isDirectory(split)) return split;
        Path legacy = dir.resolve("FurnitureData.json");
        return Files.exists(legacy) ? legacy : null;
    }
```

- [ ] **Step 2: Compile**

Run: `mvn -q test-compile`
Expected: BUILD SUCCESS (existing tests still compile; the `boolean` constructor is unchanged).

- [ ] **Step 3: Re-run the provider tests (regression)**

Run: `mvn -q test -Dtest=FurnitureTextProviderTest`
Expected: PASS — 6 tests (unchanged).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/eu/habbo/habbohotel/items/FurnitureTextProvider.java
git commit -m "feat(items): config-driven furnidata source resolution + init"
```

---

## Task 5: Construct the provider in `GameEnvironment` and expose a getter

**Files:**
- Modify: `src/main/java/com/eu/habbo/habbohotel/GameEnvironment.java`

- [ ] **Step 1: Add the field**

Near the other manager fields (around `private ItemManager itemManager;` at line 48), add:

```java
    private FurnitureTextProvider furnitureTextProvider;
```

Add the import with the other `com.eu.habbo.habbohotel.items.*` imports:

```java
import com.eu.habbo.habbohotel.items.FurnitureTextProvider;
```

- [ ] **Step 2: Construct + init it right after ItemManager loads**

In `load()`, immediately after line 79 (`this.itemManager.load();`), insert:

```java
        this.furnitureTextProvider = new FurnitureTextProvider();
        this.furnitureTextProvider.init();
```

- [ ] **Step 3: Add the getter**

Near `public ItemManager getItemManager()` (line 157), add:

```java
    public FurnitureTextProvider getFurnitureTextProvider() {
        return this.furnitureTextProvider;
    }
```

- [ ] **Step 4: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/eu/habbo/habbohotel/GameEnvironment.java
git commit -m "feat(items): construct FurnitureTextProvider after ItemManager load"
```

---

## Task 6: Add `Item.getDisplayName()`

**Files:**
- Modify: `src/main/java/com/eu/habbo/habbohotel/items/Item.java`

- [ ] **Step 1: Add the method**

In `Item.java`, after `getFullName()` (line 166-168), add:

```java
    /**
     * Display name for user-facing/log output, sourced from furnidata (by classname).
     * Falls back to the DB public_name when furnidata has no entry or names are disabled.
     * Never returns null.
     */
    public String getDisplayName() {
        FurnitureTextProvider provider = Emulator.getGameEnvironment().getFurnitureTextProvider();
        String name = (provider != null) ? provider.getName(this.name) : null;
        return (name != null && !name.isBlank()) ? name : this.fullName;
    }
```

`Emulator` is already imported in `Item.java` (line 3). `FurnitureTextProvider` is in the same package — no import needed.

- [ ] **Step 2: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/eu/habbo/habbohotel/items/Item.java
git commit -m "feat(items): Item.getDisplayName() — furnidata name with public_name fallback"
```

---

## Task 7: Swap the 6 server-side name emitters

Each is a one-line change: `getFullName()` → `getDisplayName()`. Do not touch `getName()` sites (technical).

**Files:**
- Modify: `CatalogManager.java:1057,1063`, `CatalogBuyItemAsGiftEvent.java:251,262`, `WiredTextPlaceholderUtil.java:282`, `WatchAndEarnRewardComposer.java:21`

- [ ] **Step 1: CatalogManager (2 sites)**

In `src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java`, lines 1057 and 1063, change:

`...iterator().next().getFullName()` → `...iterator().next().getDisplayName()`

(Both occurrences of `item.getBaseItems().iterator().next().getFullName()` become `...getDisplayName()`.)

- [ ] **Step 2: CatalogBuyItemAsGiftEvent (2 sites)**

In `src/main/java/com/eu/habbo/messages/incoming/catalog/CatalogBuyItemAsGiftEvent.java`, lines 251 and 262, change the same `getFullName()` → `getDisplayName()` on `item.getBaseItems().iterator().next()`.

- [ ] **Step 3: WiredTextPlaceholderUtil (1 site)**

In `src/main/java/com/eu/habbo/habbohotel/wired/core/WiredTextPlaceholderUtil.java`, line 282:

```java
            String furniName = item.getBaseItem().getDisplayName();
```

(Leave the existing `getName()` fallback at lines 283-285 — `getDisplayName()` already falls back to `public_name`; the `getName()` step remains as an ultimate fallback for blank names.)

- [ ] **Step 4: WatchAndEarnRewardComposer (1 site)**

In `src/main/java/com/eu/habbo/messages/outgoing/unknown/WatchAndEarnRewardComposer.java`, line 21:

```java
        this.response.appendString(this.item.getDisplayName());
```

- [ ] **Step 5: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Verify no stray furni display-name `getFullName()` remains**

Run: `grep -rn "getFullName()" src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java src/main/java/com/eu/habbo/messages/incoming/catalog/CatalogBuyItemAsGiftEvent.java src/main/java/com/eu/habbo/habbohotel/wired/core/WiredTextPlaceholderUtil.java src/main/java/com/eu/habbo/messages/outgoing/unknown/WatchAndEarnRewardComposer.java`
Expected: no matches in the 6 swapped lines (other `getFullName()` on non-item objects, if any, are unrelated and fine).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java src/main/java/com/eu/habbo/messages/incoming/catalog/CatalogBuyItemAsGiftEvent.java src/main/java/com/eu/habbo/habbohotel/wired/core/WiredTextPlaceholderUtil.java src/main/java/com/eu/habbo/messages/outgoing/unknown/WatchAndEarnRewardComposer.java
git commit -m "feat(items): source server-pronounced furni names from furnidata (6 sites)"
```

---

## Task 8: Full build + test + manual acceptance

**Files:** none (verification only)

- [ ] **Step 1: Full build with tests**

Run: `mvn -q clean package`
Expected: BUILD SUCCESS; FurnidataReaderTest (4) + FurnitureTextProviderTest (6) + SmokeTest (1) all pass.

- [ ] **Step 2: Manual acceptance (against a running hotel)**

1. Ensure `furni.editor.asset.base.path` points at the furnidata (or set `items.furnidata.path`).
2. Pick a furni whose furnidata `name` differs from its DB `public_name`; trigger a server-pronounced name:
   - a wired sign using `%furni.name%` → shows the furnidata name;
   - a Watch&Earn reward for that furni → notification shows the furnidata name;
   - exceed an LTD daily limit on that item → the alert shows the furnidata name.
3. Set `items.furnidata.names.enabled=false`, restart → all three revert to the DB `public_name` (toggle works).
4. Corrupt the furnidata file, restart → server boots cleanly; names fall back to `public_name` (fail-safe; check log warning).

- [ ] **Step 3: Final commit (if any config docs were added)**

```bash
git add -A
git commit -m "docs(items): document items.furnidata.* config keys" --allow-empty
```

---

## Notes for the implementer

- **No DB migration, no new packet, no client/renderer change** in this plan. Liveness (file-watch + delta broadcast + renderer) is Piece 2, a separate plan.
- **Do not touch** the furni-editor (`FurniDataManager`, `messages/incoming/furnieditor/*`, `messages/outgoing/furnieditor/*`). The reader is a new neutral class even though it overlaps in spirit.
- **Do not change** `getName()`/`item_name` sites (technical), `isPet/isBot`, or the wired `wf_` fallback in `Item.load`.
- `release=21` / `source=target=19` in `pom.xml` is intentional — do not "fix" it. JUnit Jupiter 5.10 and records are fine on 19.
