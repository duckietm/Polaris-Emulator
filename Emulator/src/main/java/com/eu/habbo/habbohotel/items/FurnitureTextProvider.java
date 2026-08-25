package com.eu.habbo.habbohotel.items;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(FurnitureTextProvider.class);
    private static final long DEFAULT_MAX_BYTES = 64L * 1024 * 1024;

    private final boolean enabled;
    private volatile Map<String, FurniText> index = Map.of();
    private volatile Path source;
    private volatile String sourceDescription = "unknown";
    private FurnidataWatcher watcher;

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
            this.source = resolveSource();
            if (this.source == null) {
                LOGGER.warn("FurnitureTextProvider: no furnidata source resolved - names fall back to public_name");
                return;
            }
            reindex(new FurnidataReader(this.source, DEFAULT_MAX_BYTES).read());
            LOGGER.info("Furniture Text Provider -> Indexed! ({} names, source: {})", this.index.size(), this.sourceDescription);

            if (Boolean.parseBoolean(Emulator.getConfig().getValue("items.furnidata.watch.enabled", "true"))) {
                if (this.watcher != null) this.watcher.stop();
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

    /** Returns {@code true} when the resolved source is a directory (split-tier layout). */
    public boolean isSourceDirectory() {
        return this.source != null && Files.isDirectory(this.source);
    }

    /** Returns the byte cap used when reading furnidata files. */
    public long getMaxBytes() {
        return Long.parseLong(com.eu.habbo.Emulator.getConfig().getValue("items.furnidata.max.bytes", String.valueOf(DEFAULT_MAX_BYTES)));
    }

    /**
     * Re-reads the furnidata from the current source and reindexes atomically.
     * Returns the delta list (new/changed entries) from {@link #reindex(List)}.
     * Never throws — returns an empty list when the source is unavailable.
     */
    public java.util.List<FurnidataEntry> reindexFromSource() {
        try {
            if (this.source == null) return java.util.List.of();
            return reindex(new FurnidataReader(this.source, DEFAULT_MAX_BYTES).read());
        } catch (Exception e) {
            LOGGER.warn("FurnitureTextProvider.reindexFromSource failed", e);
            return java.util.List.of();
        }
    }

    private Path resolveSource() {
        FurnidataSourceResolver.Source source = FurnidataSourceResolver.resolve();
        if (source.ok()) {
            this.sourceDescription = source.message();
            return source.path();
        }
        LOGGER.warn("FurnitureTextProvider: no furnidata source resolved ({}) - {}", source.status(), source.message());
        return null;
    }

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
        base = base.trim().toLowerCase(Locale.ROOT);
        return base.isEmpty() ? null : base;
    }

    /**
     * Cap length, strip control chars/newlines, neutralize % (placeholder-injection safe).
     * The 256 cap is in Java {@code char} units (UTF-16 code units), which is acceptable for
     * furni names (controlled, predominantly ASCII source). Lone/astral surrogates are not
     * specially handled.
     */
    public static String sanitize(String value) {
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

    /**
     * Returns all lowercased base classnames whose furnidata display name contains
     * {@code query} (case-insensitive, substring). Results are capped at 200 to
     * bound SQL IN-clause size. Returns an empty list when query is null/blank.
     */
    public java.util.List<String> findClassnamesByName(String query) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (query == null) return out;
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return out;
        Map<String, FurniText> idx = this.index; // local ref (volatile)
        for (Map.Entry<String, FurniText> e : idx.entrySet()) {
            FurniText t = e.getValue();
            if (t != null && t.name() != null && t.name().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(e.getKey()); // key is the lowercased base classname
                if (out.size() >= 200) break; // bound IN-clause size
            }
        }
        return out;
    }

    private record FurniText(int id, FurnitureType type, String name, String description) {}
}
