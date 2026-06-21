package com.eu.habbo.habbohotel.items;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comment-preserving, atomic, backed-up writer for furnidata name/description, keyed by
 * classname. Supports single-file and split-tier (writes the tier that currently resolves
 * the classname). Edit-only: refuses classnames absent from the furnidata.
 */
public class FurnidataWriter {

    /** Default tier names in override order (later = higher priority, wins on conflict). */
    private static final List<String> DEFAULT_TIERS = Arrays.asList("core", "custom", "seasonal");

    /** Manifest filenames tried in order (json5 first, plain json second). */
    private static final List<String> MANIFEST_NAMES = Arrays.asList("manifest.json5", "manifest.json");

    private final Path source;        // file (single) or base dir (split-tier)
    private final boolean directory;  // true => split-tier
    private final long maxBytes;
    private final int backupKeep;

    public FurnidataWriter(Path source, boolean directory, long maxBytes, int backupKeep) {
        this.source = source;
        this.directory = directory;
        this.maxBytes = maxBytes;
        this.backupKeep = Math.max(1, backupKeep);
    }

    /** @return true if an entry for classname was found and written. */
    public boolean write(String classname, String name, String description) throws IOException {
        String cn = classname == null ? "" : classname.trim().toLowerCase(java.util.Locale.ROOT);
        if (cn.isEmpty()) return false;
        String safeName = FurnitureTextProvider.sanitize(name);
        String safeDesc = FurnitureTextProvider.sanitize(description);

        Path target = locateFile(cn);
        if (target == null) return false;

        String raw = Files.readString(target, StandardCharsets.UTF_8);
        String edited = replaceEntryFields(raw, cn, safeName, safeDesc);
        if (edited == null || edited.equals(raw)) {
            // classname not present in this file, or no change
            return edited != null && !edited.equals(raw);
        }
        backup(target);
        atomicWrite(target, edited);
        return true;
    }

    /** Outcome of a {@link #create} attempt. */
    public enum CreateResult { CREATED, ALREADY_EXISTS, ID_COLLISION, NO_TARGET, IO_ERROR }

    /**
     * Append a brand-new furnidata entry (upsert's "create" half). Refuses if the
     * classname already exists (caller should edit instead) or if {@code id} is
     * already used by a DIFFERENT classname (id collision would break the
     * {@code roomItem.name.<id>} / typeId resolution on the renderer). The complete
     * entry object is built by the caller (see FurnidataEntryBuilder) and inserted
     * right after the opening '[' of the matching section's "furnitype" array.
     *
     * @param classname  new classname (must be absent from furnidata)
     * @param id         furnidata id (= item sprite id); must not collide
     * @param type       FLOOR -> roomitemtypes, WALL -> wallitemtypes
     * @param entryJson5 the complete entry object as a single-line JSON5 string
     * @param createTier split-tier only: the tier dir to write into (e.g. "custom"); ignored for single-file
     */
    public CreateResult create(String classname, int id, FurnitureType type, String entryJson5, String createTier) {
        String cn = classname == null ? "" : classname.trim().toLowerCase(java.util.Locale.ROOT);
        if (cn.isEmpty() || entryJson5 == null || entryJson5.isBlank()) return CreateResult.NO_TARGET;

        // Guard: duplicate classname / id collision (scan the whole source).
        for (FurnidataEntry e : new FurnidataReader(source, maxBytes).read()) {
            String ecn = e.classname() == null ? "" : e.classname().trim().toLowerCase(java.util.Locale.ROOT);
            if (ecn.equals(cn)) return CreateResult.ALREADY_EXISTS;
            if (e.id() == id) return CreateResult.ID_COLLISION;
        }

        try {
            Path target = resolveCreateTarget(createTier);
            if (target == null) return CreateResult.NO_TARGET;

            String raw = Files.readString(target, StandardCharsets.UTF_8);
            String section = (type == FurnitureType.WALL) ? "wallitemtypes" : "roomitemtypes";
            int open = furnitypeArrayOpenIndex(raw, section);
            if (open < 0) return CreateResult.NO_TARGET; // section/array absent in target file

            String edited = raw.substring(0, open) + "\n" + entryJson5 + "," + raw.substring(open);
            backup(target);
            atomicWrite(target, edited);
            return CreateResult.CREATED;
        } catch (IOException e) {
            return CreateResult.IO_ERROR;
        }
    }

    /** Single-file: the source. Split-tier: the create-tier file (created with a shell if absent). */
    private Path resolveCreateTarget(String createTier) throws IOException {
        if (!directory) return source;
        String tier = (createTier == null || createTier.isBlank()) ? "custom" : createTier.trim();
        Path base = source.toAbsolutePath().normalize();
        Path tierDir = safeResolve(base, tier);
        if (tierDir == null) return null;
        if (!Files.isDirectory(tierDir)) Files.createDirectories(tierDir);
        for (String fileName : manifestList(tierDir, "files", List.of())) {
            Path f = safeResolve(base, tierDir.resolve(fileName).toString());
            if (f != null && Files.isRegularFile(f)) return f;
        }
        Path def = tierDir.resolve("furnidata.json5");
        if (!Files.exists(def)) {
            Files.writeString(def,
                "{\n  \"roomitemtypes\": { \"furnitype\": [\n] },\n  \"wallitemtypes\": { \"furnitype\": [\n] }\n}\n",
                StandardCharsets.UTF_8);
        }
        return def;
    }

    /** Index just after the '[' that opens {@code <section>.furnitype}, or -1 if absent. String-aware. */
    static int furnitypeArrayOpenIndex(String raw, String section) {
        int s = indexOfKey(raw, section, 0);
        if (s < 0) return -1;
        int ft = indexOfKey(raw, "furnitype", s);
        if (ft < 0) return -1;
        boolean inStr = false; char q = 0;
        for (int i = ft; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inStr) { if (c == '\\') i++; else if (c == q) inStr = false; continue; }
            if (c == '"' || c == '\'') { inStr = true; q = c; }
            else if (c == '[') return i + 1;
        }
        return -1;
    }

    /** First occurrence of a quoted key ("key" or 'key') at/after {@code from}, or -1. */
    private static int indexOfKey(String raw, String key, int from) {
        int a = raw.indexOf("\"" + key + "\"", from);
        int b = raw.indexOf("'" + key + "'", from);
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    /** For single-file just returns the file; for split-tier, the tier file that contains cn. */
    private Path locateFile(String cn) throws IOException {
        if (!directory) {
            // confirm existence via the reader (size-guarded, parses the same way)
            return containsClassname(source, cn) ? source : null;
        }
        // split-tier: iterate tiers in OVERRIDE order (later tiers win); pick the last containing cn
        Path winner = null;
        for (Path tierFile : splitTierFilesInOrder()) {
            if (containsClassname(tierFile, cn)) winner = tierFile;
        }
        return winner;
    }

    private boolean containsClassname(Path file, String cn) {
        for (FurnidataEntry e : new FurnidataReader(file, maxBytes).read()) {
            if (e.classname() != null && e.classname().trim().toLowerCase(java.util.Locale.ROOT).equals(cn)) return true;
        }
        return false;
    }

    /**
     * Replace the "name" and "description" string values inside the JSON object that holds
     * "classname": "<cn>". Preserves everything else (comments, ordering, formatting).
     * Handles double- and single-quoted JSON5 keys/values. Returns null if cn not found.
     */
    static String replaceEntryFields(String raw, String cn, String name, String description) {
        // find the classname value occurrence (case-insensitive on the value)
        Pattern classProp = Pattern.compile(
            "([\"'])classname\\1\\s*:\\s*([\"'])((?:\\\\.|(?!\\2).)*)\\2", Pattern.CASE_INSENSITIVE);
        Matcher m = classProp.matcher(raw);
        int objStart = -1, objEnd = -1;
        while (m.find()) {
            String val = m.group(3).trim().toLowerCase(java.util.Locale.ROOT);
            if (!val.equals(cn)) continue;
            // expand to the enclosing { ... }
            objStart = lastUnbalancedBrace(raw, m.start());
            objEnd = matchingClose(raw, objStart);
            break;
        }
        if (objStart < 0 || objEnd < 0) return null;
        String obj = raw.substring(objStart, objEnd + 1);
        String newObj = replaceField(obj, "name", name);
        newObj = replaceField(newObj, "description", description);
        return raw.substring(0, objStart) + newObj + raw.substring(objEnd + 1);
    }

    private static String replaceField(String obj, String field, String value) {
        Pattern p = Pattern.compile(
            "(([\"'])" + Pattern.quote(field) + "\\2\\s*:\\s*)([\"'])((?:\\\\.|(?!\\3).)*)\\3");
        Matcher m = p.matcher(obj);
        if (!m.find()) return obj; // field absent → leave object as-is
        String replacement = m.group(1) + '"' + jsonEscape(value) + '"';
        return obj.substring(0, m.start()) + replacement + obj.substring(m.end());
    }

    private static int lastUnbalancedBrace(String s, int from) {
        int depth = 0;
        for (int i = from; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == '}') depth++;
            else if (c == '{') { if (depth == 0) return i; depth--; }
        }
        return -1;
    }

    private static int matchingClose(String s, int open) {
        int depth = 0; boolean inStr = false; char q = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) { if (c == '\\') { i++; } else if (c == q) inStr = false; continue; }
            if (c == '"' || c == '\'') { inStr = true; q = c; }
            else if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private static String jsonEscape(String v) {
        StringBuilder b = new StringBuilder(v.length() + 8);
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else b.append(c);
        }
        return b.toString();
    }

    /**
     * Enumerate every data file reachable from the split-tier base directory, in
     * override order (core → custom → seasonal, or the order declared in the top-level
     * {@code manifest.json(5)}).  Within each tier the per-tier manifest's {@code files}
     * array determines the file order.
     *
     * <p>All resolved paths are checked against the normalised base directory via
     * {@link #safeResolve}: any entry that would escape the base is silently skipped.
     *
     * @return ordered list of existing, in-bounds data files (earliest tier first).
     */
    private List<Path> splitTierFilesInOrder() throws IOException {
        Path base = source.toAbsolutePath().normalize();
        List<String> tiers = manifestList(base, "tiers", DEFAULT_TIERS);
        List<Path> result = new ArrayList<>();

        for (String tier : tiers) {
            Path tierDir = safeResolve(base, tier);
            if (tierDir == null || !Files.isDirectory(tierDir)) continue;

            for (String fileName : manifestList(tierDir, "files", List.of())) {
                Path file = safeResolve(base, tierDir.resolve(fileName).toString());
                if (file == null || !Files.isRegularFile(file)) continue;
                result.add(file);
            }
        }
        return result;
    }

    /**
     * Resolve {@code entry} relative to {@code base} and verify the result stays
     * inside {@code base} (path-traversal guard).
     *
     * @param base  the normalised absolute base directory.
     * @param entry a path string (may be relative or absolute, may contain {@code ..}).
     * @return the normalised absolute path if it is inside {@code base}; {@code null} otherwise.
     */
    private static Path safeResolve(Path base, String entry) {
        try {
            Path resolved = base.resolve(entry).toAbsolutePath().normalize();
            return resolved.startsWith(base) ? resolved : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Read the {@code key} string-array from the first manifest file found in {@code dir}
     * ({@code manifest.json5} then {@code manifest.json}).  Falls back to {@code fallback}
     * if no manifest exists or the key is absent/empty.
     */
    private List<String> manifestList(Path dir, String key, List<String> fallback) {
        for (String name : MANIFEST_NAMES) {
            Path m = dir.resolve(name);
            if (!Files.exists(m)) continue;
            try {
                String stripped = FurnidataReader.stripJson5(
                    Files.readString(m, StandardCharsets.UTF_8));
                com.google.gson.JsonObject obj =
                    com.google.gson.JsonParser.parseString(stripped).getAsJsonObject();
                if (obj.has(key) && obj.get(key).isJsonArray()) {
                    List<String> list = new ArrayList<>();
                    for (com.google.gson.JsonElement el : obj.getAsJsonArray(key))
                        list.add(el.getAsString());
                    if (!list.isEmpty()) return list;
                }
            } catch (Exception ignored) {
                // bad manifest → fall through to next candidate / fallback
            }
        }
        return fallback;
    }

    private void backup(Path target) throws IOException {
        Path bak = target.resolveSibling(target.getFileName() + ".bak." + System.nanoTime());
        Files.copy(target, bak, StandardCopyOption.COPY_ATTRIBUTES);
        pruneBackups(target);
    }

    private void pruneBackups(Path target) throws IOException {
        String prefix = target.getFileName() + ".bak.";
        try (var stream = Files.list(target.getParent())) {
            List<Path> baks = stream.filter(p -> p.getFileName().toString().startsWith(prefix))
                .sorted(Comparator.comparingLong(p -> backupStamp(p))).toList();
            for (int i = 0; i < baks.size() - backupKeep; i++) Files.deleteIfExists(baks.get(i));
        }
    }

    private static long backupStamp(Path p) {
        String s = p.getFileName().toString();
        try { return Long.parseLong(s.substring(s.lastIndexOf('.') + 1)); } catch (Exception e) { return 0L; }
    }

    private void atomicWrite(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp." + System.nanoTime());
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Restore the most recent backup of the (single-file) target. @return true if restored. */
    public boolean revertLastBackup() throws IOException {
        if (directory) return revertSplitTier();
        return revertFile(source);
    }

    private boolean revertFile(Path target) throws IOException {
        String prefix = target.getFileName() + ".bak.";
        try (var stream = Files.list(target.getParent())) {
            Path latest = stream.filter(p -> p.getFileName().toString().startsWith(prefix))
                .max(Comparator.comparingLong(FurnidataWriter::backupStamp)).orElse(null);
            if (latest == null) return false;
            atomicWrite(target, Files.readString(latest, StandardCharsets.UTF_8));
            return true;
        }
    }

    private boolean revertSplitTier() throws IOException {
        boolean any = false;
        for (Path f : splitTierFilesInOrder()) any |= revertFile(f);
        return any;
    }
}
