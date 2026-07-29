package com.eu.habbo.habbohotel.wired.arrays;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class WiredArrayCaptureSnapshot {
    private final boolean found;
    private final int index;
    private final int length;
    private final boolean occupied;
    private final Map<String, Long> fields;

    private WiredArrayCaptureSnapshot(
            boolean found, int index, int length, boolean occupied, Map<String, Long> fields) {
        this.found = found;
        this.index = index;
        this.length = length;
        this.occupied = occupied;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public static WiredArrayCaptureSnapshot missing(int length) {
        return new WiredArrayCaptureSnapshot(false, -1, length, false, Map.of());
    }

    public static WiredArrayCaptureSnapshot found(
            WiredArrayDefinition definition, int index, int length, WiredArrayEntry entry) {
        Map<String, Long> fields = new LinkedHashMap<>();
        for (WiredArrayFieldDefinition field : definition.getFields()) {
            fields.put(field.getName().toLowerCase(Locale.ROOT), entry.getValue(field.getId()));
        }
        return new WiredArrayCaptureSnapshot(true, index, length, true, fields);
    }

    public Long read(String fieldName) {
        if (fieldName == null) return null;
        return switch (fieldName.toLowerCase(Locale.ROOT)) {
            case "found" -> this.found ? 1L : 0L;
            case "index" -> (long) this.index;
            case "length" -> (long) this.length;
            case "occupied" -> this.occupied ? 1L : 0L;
            default -> this.fields.get(fieldName.toLowerCase(Locale.ROOT));
        };
    }
}
