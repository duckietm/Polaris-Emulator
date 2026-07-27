package com.eu.habbo.habbohotel.wired.arrays;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WiredArrayEntry {
    private final Map<Integer, Long> valuesByFieldId;

    private WiredArrayEntry(Map<Integer, Long> valuesByFieldId) {
        this.valuesByFieldId = Collections.unmodifiableMap(valuesByFieldId);
    }

    public static WiredArrayEntry fromValues(WiredArrayDefinition definition, Map<Integer, Long> values) {
        if (definition == null) {
            throw new IllegalArgumentException("Array definition is required.");
        }

        Map<Integer, Long> normalized = new LinkedHashMap<>();
        for (WiredArrayFieldDefinition field : definition.getFields()) {
            Long value = values == null ? null : values.get(field.getId());
            normalized.put(field.getId(), value == null ? 0L : value);
        }
        if (values != null) {
            for (Integer fieldId : values.keySet()) {
                if (fieldId == null || definition.getField(fieldId) == null) {
                    throw new IllegalArgumentException("Unknown array field ID " + fieldId + ".");
                }
            }
        }
        return new WiredArrayEntry(normalized);
    }

    public long getValue(int fieldId) {
        Long value = this.valuesByFieldId.get(fieldId);
        if (value == null) {
            throw new IllegalArgumentException("Unknown array field ID " + fieldId + ".");
        }
        return value;
    }

    public Map<Integer, Long> valuesByFieldId() {
        return this.valuesByFieldId;
    }

    /** Value equality keeps the persistence delta to cells that actually changed. */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof WiredArrayEntry entry && this.valuesByFieldId.equals(entry.valuesByFieldId);
    }

    @Override
    public int hashCode() {
        return this.valuesByFieldId.hashCode();
    }
}
