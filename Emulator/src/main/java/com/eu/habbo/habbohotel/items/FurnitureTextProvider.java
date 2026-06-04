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
