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
                String raw = readJson5Capped(m);
                if (raw == null) continue;
                JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
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
                if (!o.has("id") || o.get("id").isJsonNull() || !o.has("classname") || o.get("classname").isJsonNull()) continue;
                out.add(new FurnidataEntry(
                    o.get("id").getAsInt(),
                    o.get("classname").getAsString(),
                    type,
                    (o.has("name") && !o.get("name").isJsonNull()) ? o.get("name").getAsString() : "",
                    (o.has("description") && !o.get("description").isJsonNull()) ? o.get("description").getAsString() : ""
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

    /**
     * Strip // and block comments and trailing commas so Gson can parse JSON5.
     * Known limitation: the trailing-comma pass is a regex over the whole output,
     * so a string value literally containing ",[whitespace]}" or ",[whitespace]]"
     * would be altered. Real Habbo furnidata names/descriptions do not contain
     * that pattern; values are additionally sanitized downstream before use.
     */
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
