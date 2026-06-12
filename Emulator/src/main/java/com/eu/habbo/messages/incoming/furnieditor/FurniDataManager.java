package com.eu.habbo.messages.incoming.furnieditor;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnidataSourceResolver;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages reading and writing of FurnitureData entries.
 *
 * Accepts both legacy single-file layouts (FurnitureData.json) and the split
 * directory layout introduced by the split-aware loader on the Nitro V3 side:
 *
 *   <base>/
 *     manifest.json5         OPTIONAL  { "tiers": ["core", "custom", "seasonal"] }
 *     core/manifest.json5    REQUIRED  { "files": ["floor-001.json5", ...] }
 *     core/*.json5
 *     custom/manifest.json5  OPTIONAL
 *     seasonal/manifest.json5 OPTIONAL
 *
 * The path is resolved from the emulator config:
 *
 *   furni.editor.renderer.config.path  -> renderer-config.json (read for the
 *                                         furnidata.url value)
 *   furni.editor.asset.base.path       -> filesystem base used to derive the
 *                                         local path from an http(s) URL
 */
public class FurniDataManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(FurniDataManager.class);

    private static final List<String> DEFAULT_TIERS = Arrays.asList("core", "custom", "seasonal");
    private static final List<String> MANIFEST_NAMES = Arrays.asList("manifest.json5", "manifest.json");
    private static final List<String> SECTIONS = Arrays.asList("roomitemtypes", "wallitemtypes");
    private static volatile CachedIndex cachedIndex = null;

    public record LookupResult(String itemJson, String diagnosticJson) {
    }

    /**
     * Get the JSON string for a specific item.
     * Returns "{}" if not found or on error.
     */
    public static String getItemJson(int itemId) {
        return getItemJson(itemId, null);
    }

    /**
     * Get the JSON string for a specific item.
     * Prefer the DB classname because items_base.id can diverge from the
     * furnidata id after imports/reconciliations. Falls back to id lookup.
     * Returns "{}" if not found or on error.
     */
    public static String getItemJson(int itemId, String classname) {
        return getItemLookup(itemId, classname).itemJson();
    }

    public static LookupResult getItemLookup(int itemId, String classname) {
        FurnidataSourceResolver.Source source = FurnidataSourceResolver.resolve();
        if (source == null || !source.ok()) {
            return new LookupResult("{}", diagnostic(source, itemId, classname, "source_missing"));
        }

        try {
            CachedIndex index = indexFor(source);

            // 1. Try exact classname match (preserves *N suffix for multicolor items)
            String key = baseClassname(classname);
            String byClassname = key != null ? index.byClassname.get(key) : null;
            if (byClassname != null) {
                return new LookupResult(byClassname, diagnostic(source, itemId, classname, "matched_classname"));
            }

            // 2. Fallback: try stripped classname (without *N suffix) for items whose
            //    furnidata entry does not include the color-variant suffix.
            String strippedKey = strippedClassname(classname);
            if (strippedKey != null && !strippedKey.equals(key)) {
                String byStripped = index.byClassname.get(strippedKey);
                if (byStripped != null) {
                    return new LookupResult(byStripped, diagnostic(source, itemId, classname, "matched_classname_stripped"));
                }
            }

            String byId = index.byId.get(itemId);
            if (byId != null) {
                return new LookupResult(byId, diagnostic(source, itemId, classname, "matched_id"));
            }

            String reason = index.empty ? "manifest_empty" : "not_found";
            return new LookupResult("{}", diagnostic(source, itemId, classname, reason));
        } catch (Exception e) {
            LOGGER.warn("Failed to read FurnitureData for item " + itemId, e);
            FurnidataSourceResolver.Source errorSource = new FurnidataSourceResolver.Source(source.path(), source.directory(), FurnidataSourceResolver.Status.ERROR, e.getMessage());
            return new LookupResult("{}", diagnostic(errorSource, itemId, classname, "error"));
        }
    }

    private static CachedIndex indexFor(FurnidataSourceResolver.Source source) {
        long signature = sourceSignature(source.path());
        String sourceKey = source.path().toAbsolutePath().normalize().toString();
        CachedIndex current = cachedIndex;
        if (current != null && current.sourceKey.equals(sourceKey) && current.signature == signature) return current;

        CachedIndex next = buildIndex(source, sourceKey, signature);
        cachedIndex = next;
        return next;
    }

    private static CachedIndex buildIndex(FurnidataSourceResolver.Source source, String sourceKey, long signature) {
        Map<Integer, String> byId = new HashMap<>();
        Map<String, String> byClassname = new HashMap<>();

        if (source.directory()) {
            indexSplitDir(source.path(), byId, byClassname);
        } else {
            try {
                String content = readJson5(source.path());
                indexRoot(JsonParser.parseString(content).getAsJsonObject(), byId, byClassname);
            } catch (Exception e) {
                LOGGER.warn("Failed to parse furnidata source {}", source.path(), e);
            }
        }

        return new CachedIndex(sourceKey, signature, Map.copyOf(byId), Map.copyOf(byClassname), byId.isEmpty() && byClassname.isEmpty());
    }

    private static void indexSplitDir(Path baseDir, Map<Integer, String> byId, Map<String, String> byClassname) {
        if (!Files.isDirectory(baseDir)) return;

        for (String tier : readTiersManifest(baseDir)) {
            Path tierDir = baseDir.resolve(tier);
            if (!Files.isDirectory(tierDir)) continue;

            for (String fileName : readFilesManifest(tierDir)) {
                Path file = tierDir.resolve(fileName);
                if (!Files.exists(file)) continue;

                try {
                    String content = readJson5(file);
                    indexRoot(JsonParser.parseString(content).getAsJsonObject(), byId, byClassname);
                } catch (Exception e) {
                    LOGGER.warn("Failed to parse split gamedata file " + file, e);
                }
            }
        }
    }

    private static void indexRoot(JsonObject root, Map<Integer, String> byId, Map<String, String> byClassname) {
        for (String section : SECTIONS) {
            if (!root.has(section)) continue;
            JsonObject sectionObj = root.getAsJsonObject(section);
            if (!sectionObj.has("furnitype")) continue;

            for (JsonElement el : sectionObj.getAsJsonArray("furnitype")) {
                JsonObject obj = el.getAsJsonObject();
                String json = obj.toString();

                if (obj.has("id")) byId.put(obj.get("id").getAsInt(), json);
                if (obj.has("classname")) {
                    String key = baseClassname(obj.get("classname").getAsString());
                    if (key != null) byClassname.put(key, json);
                }
            }
        }
    }

    private static long sourceSignature(Path source) {
        try {
            if (source == null || !Files.exists(source)) return -1L;
            if (!Files.isDirectory(source)) return Files.getLastModifiedTime(source).toMillis() ^ Files.size(source);

            final long[] signature = { 17L };
            try (var stream = Files.walk(source)) {
                stream.filter(Files::isRegularFile).forEach(path -> {
                    try {
                        signature[0] = (signature[0] * 31L) ^ Files.getLastModifiedTime(path).toMillis() ^ Files.size(path);
                    } catch (Exception ignored) {
                    }
                });
            }
            return signature[0];
        } catch (Exception e) {
            return System.nanoTime();
        }
    }

    private static String diagnostic(FurnidataSourceResolver.Source source, int itemId, String classname, String reason) {
        JsonObject obj = new JsonObject();
        obj.addProperty("reason", reason);
        obj.addProperty("itemId", itemId);
        obj.addProperty("classname", classname != null ? classname : "");
        obj.addProperty("sourcePath", source != null && source.path() != null ? source.path().toString() : "");
        obj.addProperty("sourceDirectory", source != null && source.directory());
        obj.addProperty("sourceStatus", source != null ? source.status().name() : "CONFIG_MISSING");
        obj.addProperty("message", source != null && source.message() != null ? source.message() : "");
        return obj.toString();
    }

    private record CachedIndex(String sourceKey, long signature, Map<Integer, String> byId, Map<String, String> byClassname, boolean empty) {
    }

    static String findItemJson(Path source, boolean directory, int itemId, String classname) {
        try {
            if (directory) {
                return findItemInSplitDir(source, itemId, classname);
            }

            if (!Files.exists(source)) return "{}";

            String content = readJson5(source);
            String found = findItemInRoot(JsonParser.parseString(content).getAsJsonObject(), itemId, classname);
            return found != null ? found : "{}";
        } catch (Exception e) {
            LOGGER.warn("Failed to read FurnitureData for item " + itemId, e);
        }

        return "{}";
    }

    private static String findItemInRoot(JsonObject root, int itemId) {
        return findItemInRoot(root, itemId, null);
    }

    private static String findItemInRoot(JsonObject root, int itemId, String classname) {
        String byClassname = findItemInRootByClassname(root, classname);
        if (byClassname != null) return byClassname;

        for (String section : SECTIONS) {
            if (!root.has(section)) continue;
            JsonObject sectionObj = root.getAsJsonObject(section);
            if (!sectionObj.has("furnitype")) continue;
            JsonArray types = sectionObj.getAsJsonArray("furnitype");

            for (JsonElement el : types) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("id") && obj.get("id").getAsInt() == itemId) {
                    return obj.toString();
                }
            }
        }
        return null;
    }

    private static String findItemInRootByClassname(JsonObject root, String classname) {
        String wanted = baseClassname(classname);
        if (wanted == null) return null;

        for (String section : SECTIONS) {
            if (!root.has(section)) continue;
            JsonObject sectionObj = root.getAsJsonObject(section);
            if (!sectionObj.has("furnitype")) continue;
            JsonArray types = sectionObj.getAsJsonArray("furnitype");

            for (JsonElement el : types) {
                JsonObject obj = el.getAsJsonObject();
                if (!obj.has("classname")) continue;

                // Try exact match first (preserves *N suffix)
                String actual = baseClassname(obj.get("classname").getAsString());
                if (wanted.equals(actual)) return obj.toString();
            }
        }

        // Fallback: try stripped classname (without *N suffix)
        String strippedWanted = strippedClassname(classname);
        if (strippedWanted != null && !strippedWanted.equals(wanted)) {
            for (String section : SECTIONS) {
                if (!root.has(section)) continue;
                JsonObject sectionObj = root.getAsJsonObject(section);
                if (!sectionObj.has("furnitype")) continue;
                JsonArray types = sectionObj.getAsJsonArray("furnitype");

                for (JsonElement el : types) {
                    JsonObject obj = el.getAsJsonObject();
                    if (!obj.has("classname")) continue;

                    String actual = strippedClassname(obj.get("classname").getAsString());
                    if (strippedWanted.equals(actual)) return obj.toString();
                }
            }
        }

        return null;
    }

    /**
     * Normalize a classname for index/lookup.
     *
     * Preserves the full classname including any {@code *N} color-variant suffix
     * so that multicolor items (e.g. {@code rare_dragonlamp*1}, {@code rare_dragonlamp*2})
     * each get their own index entry.  The stripped variant (without {@code *N}) is
     * used as a fallback during lookup for items whose furnidata entry does not
     * include the suffix.
     */
    private static String baseClassname(String classname) {
        if (classname == null) return null;
        String base = classname.trim().toLowerCase(java.util.Locale.ROOT);
        return base.isEmpty() ? null : base;
    }

    /**
     * Like {@link #baseClassname(String)} but strips any trailing {@code *N}
     * color-variant suffix.  Used as a fallback during lookup.
     */
    private static String strippedClassname(String classname) {
        if (classname == null) return null;
        int star = classname.indexOf('*');
        String base = star >= 0 ? classname.substring(0, star) : classname;
        base = base.trim().toLowerCase(java.util.Locale.ROOT);
        return base.isEmpty() ? null : base;
    }

    /**
     * Walk the split directory layout looking for an item by id.
     * Later tiers (custom, then seasonal) override earlier ones.
     */
    private static String findItemInSplitDir(Path baseDir, int itemId, String classname) {
        if (!Files.isDirectory(baseDir)) return "{}";

        List<String> tiers = readTiersManifest(baseDir);
        String found = null;

        for (String tier : tiers) {
            Path tierDir = baseDir.resolve(tier);
            if (!Files.isDirectory(tierDir)) continue;

            List<String> files = readFilesManifest(tierDir);
            for (String fileName : files) {
                Path file = tierDir.resolve(fileName);
                if (!Files.exists(file)) continue;

                try {
                    String content = readJson5(file);
                    JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
                    String match = findItemInRoot(obj, itemId, classname);
                    if (match != null) found = match;
                } catch (Exception e) {
                    LOGGER.warn("Failed to parse split gamedata file " + file, e);
                }
            }
        }

        return found != null ? found : "{}";
    }

    @SuppressWarnings("unchecked")
    private static List<String> readTiersManifest(Path baseDir) {
        Path manifest = firstExisting(baseDir, MANIFEST_NAMES);
        if (manifest == null) return DEFAULT_TIERS;

        try {
            String content = readJson5(manifest);
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            if (obj.has("tiers") && obj.get("tiers").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("tiers");
                List<String> out = new java.util.ArrayList<>();
                for (JsonElement el : arr) out.add(el.getAsString());
                if (!out.isEmpty()) return out;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read root manifest " + manifest + ", falling back to default tiers", e);
        }
        return DEFAULT_TIERS;
    }

    private static List<String> readFilesManifest(Path tierDir) {
        Path manifest = firstExisting(tierDir, MANIFEST_NAMES);
        if (manifest == null) return java.util.Collections.emptyList();

        try {
            String content = readJson5(manifest);
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            if (obj.has("files") && obj.get("files").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("files");
                List<String> out = new java.util.ArrayList<>();
                for (JsonElement el : arr) out.add(el.getAsString());
                return out;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read tier manifest " + manifest, e);
        }
        return java.util.Collections.emptyList();
    }

    private static Path firstExisting(Path dir, List<String> names) {
        for (String name : names) {
            Path p = dir.resolve(name);
            if (Files.exists(p)) return p;
        }
        return null;
    }

    /**
     * Read a JSON or JSON5 file. Strips line and block comments and trailing
     * commas so Gson can parse the result. String contents are preserved
     * verbatim; comments embedded inside strings are not removed.
     */
    private static String readJson5(Path path) throws IOException {
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        return stripJson5(raw);
    }

    static String stripJson5(String content) {
        if (content == null || content.isEmpty()) return content;

        StringBuilder out = new StringBuilder(content.length());
        int i = 0;
        int len = content.length();
        boolean inString = false;
        char stringChar = 0;
        boolean escape = false;

        while (i < len) {
            char c = content.charAt(i);

            if (inString) {
                out.append(c);
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == stringChar) {
                    inString = false;
                }
                i++;
                continue;
            }

            if (c == '"' || c == '\'') {
                inString = true;
                stringChar = c;
                out.append(c);
                i++;
                continue;
            }

            if (c == '/' && i + 1 < len) {
                char next = content.charAt(i + 1);
                if (next == '/') {
                    int eol = content.indexOf('\n', i + 2);
                    if (eol < 0) { i = len; break; }
                    i = eol;
                    continue;
                }
                if (next == '*') {
                    int end = content.indexOf("*/", i + 2);
                    if (end < 0) { i = len; break; }
                    i = end + 2;
                    continue;
                }
            }

            out.append(c);
            i++;
        }

        String stripped = out.toString();
        // Remove trailing commas before } or ]
        stripped = stripped.replaceAll(",(\\s*[}\\]])", "$1");
        return stripped;
    }

    /**
     * Represents the resolved location of the furnidata source: either a single
     * file or a directory in split-layout mode.
     */
    static class ResolvedSource {
        final Path path;
        final boolean directory;

        ResolvedSource(Path path, boolean directory) {
            this.path = path;
            this.directory = directory;
        }
    }

    /**
     * Resolve the location of the furnidata source. Returns null if no
     * candidate can be found.
     */
    private static ResolvedSource resolveSource() {
        try {
            String configPath = Emulator.getConfig().getValue("furni.editor.renderer.config.path", "");

            if (configPath.isEmpty()) {
                Path fallback = fallbackToBasePath();
                return fallback != null ? new ResolvedSource(fallback, Files.isDirectory(fallback)) : null;
            }

            Path rendererConfig = Paths.get(configPath);
            if (!Files.exists(rendererConfig)) return null;

            String rendererContent = readJson5(rendererConfig);
            JsonObject rendererObj = JsonParser.parseString(rendererContent).getAsJsonObject();

            if (!rendererObj.has("furnidata.url")) return null;

            String furniUrl = expandRendererUrl(rendererObj, "furnidata.url");

            if (hasUnresolvedPathPlaceholder(furniUrl)) {
                Path fallback = fallbackToBasePath();
                return fallback != null ? new ResolvedSource(fallback, Files.isDirectory(fallback)) : null;
            }

            // Strip query string and fragment (e.g. ?v=123 or #anchor)
            String cleanUrl = furniUrl;
            int q = cleanUrl.indexOf('?');
            if (q >= 0) cleanUrl = cleanUrl.substring(0, q);
            int h = cleanUrl.indexOf('#');
            if (h >= 0) cleanUrl = cleanUrl.substring(0, h);

            boolean splitMode = cleanUrl.endsWith("/");

            // Local file path (not http) — return as-is, the caller will check
            // whether it points at a file or a directory.
            if (!cleanUrl.startsWith("http")) {
                Path local = Paths.get(cleanUrl);
                return new ResolvedSource(local, splitMode || Files.isDirectory(local));
            }

            String basePath = Emulator.getConfig().getValue("furni.editor.asset.base.path", "");
            if (basePath.isEmpty()) return null;

            ResolvedSource mapped = toLocalSource(Paths.get(basePath), furniUrl);
            if (mapped != null) return mapped;

            if (splitMode) {
                // Derive the directory name from the URL: take the last non-empty
                // segment before the trailing slash. e.g. https://x/y/furnidata/ -> "furnidata"
                String trimmed = cleanUrl.endsWith("/") ? cleanUrl.substring(0, cleanUrl.length() - 1) : cleanUrl;
                String dirName = trimmed.substring(trimmed.lastIndexOf('/') + 1);
                Path candidate = Paths.get(basePath, dirName);
                return new ResolvedSource(candidate, true);
            }

            String filename = cleanUrl.substring(cleanUrl.lastIndexOf('/') + 1);
            Path candidate = Paths.get(basePath, filename);
            return new ResolvedSource(candidate, false);
        } catch (Exception e) {
            LOGGER.warn("Failed to resolve FurnitureData source", e);
        }

        return null;
    }

    private static Path fallbackToBasePath() {
        String basePath = Emulator.getConfig().getValue("furni.editor.asset.base.path", "");
        if (basePath.isEmpty()) return null;
        Path dir = Paths.get(basePath);
        // Prefer the split layout if it exists, then the legacy file.
        Path splitCandidate = dir.resolve("furnidata");
        if (Files.isDirectory(splitCandidate)) return splitCandidate;
        Path legacy = dir.resolve("FurnitureData.json");
        if (Files.exists(legacy)) return legacy;
        return null;
    }

    static String expandRendererUrl(JsonObject rendererObj, String key) {
        if (rendererObj == null || !rendererObj.has(key)) return "";

        String value = rendererObj.get(key).getAsString();
        for (int i = 0; i < 10; i++) {
            int start = value.indexOf("${");
            if (start < 0) break;

            int end = value.indexOf('}', start + 2);
            if (end < 0) break;

            String placeholder = value.substring(start + 2, end);
            if (!rendererObj.has(placeholder)) break;

            String replacement = rendererObj.get(placeholder).getAsString();
            value = value.substring(0, start) + replacement + value.substring(end + 1);
        }

        return value;
    }

    private static boolean hasUnresolvedPathPlaceholder(String value) {
        if (value == null) return false;

        String pathOnly = stripQueryAndFragment(value);
        return pathOnly.contains("${");
    }

    static ResolvedSource toLocalSource(Path assetBase, String furniUrl) {
        if (furniUrl == null || furniUrl.isBlank()) return null;

        String cleanUrl = stripQueryAndFragment(furniUrl);
        boolean splitMode = cleanUrl.endsWith("/");

        if (!cleanUrl.startsWith("http")) {
            Path local = Paths.get(cleanUrl);
            return new ResolvedSource(local, splitMode || Files.isDirectory(local));
        }

        if (assetBase == null) return null;

        String urlPath;
        try {
            urlPath = URI.create(cleanUrl).getPath();
        } catch (Exception e) {
            int scheme = cleanUrl.indexOf("://");
            int pathStart = scheme >= 0 ? cleanUrl.indexOf('/', scheme + 3) : -1;
            urlPath = pathStart >= 0 ? cleanUrl.substring(pathStart) : cleanUrl;
        }

        String normalizedUrlPath = urlPath.replace('\\', '/');
        String baseName = assetBase.getFileName() != null ? assetBase.getFileName().toString() : "";
        String marker = "/" + baseName + "/";

        Path candidate;
        int markerIndex = baseName.isEmpty() ? -1 : normalizedUrlPath.indexOf(marker);
        if (markerIndex >= 0) {
            String relative = normalizedUrlPath.substring(markerIndex + marker.length());
            candidate = assetBase.resolve(relative);
        } else if (splitMode) {
            String trimmed = normalizedUrlPath.endsWith("/")
                ? normalizedUrlPath.substring(0, normalizedUrlPath.length() - 1)
                : normalizedUrlPath;
            String dirName = trimmed.substring(trimmed.lastIndexOf('/') + 1);
            candidate = assetBase.resolve(dirName);
        } else {
            String filename = normalizedUrlPath.substring(normalizedUrlPath.lastIndexOf('/') + 1);
            candidate = assetBase.resolve(filename);
        }

        return new ResolvedSource(candidate, splitMode || Files.isDirectory(candidate));
    }

    private static String stripQueryAndFragment(String value) {
        String out = value;
        int q = out.indexOf('?');
        if (q >= 0) out = out.substring(0, q);
        int h = out.indexOf('#');
        if (h >= 0) out = out.substring(0, h);
        return out;
    }
}
