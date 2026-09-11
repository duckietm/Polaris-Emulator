package com.eu.habbo.habbohotel.wired.arrays;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable array schema. Field IDs are stable so renames do not rewrite stored entries. */
public final class WiredArrayDefinition {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_FIELDS = 8;
    public static final int MAX_FIELD_ID = 1_000_000;
    public static final int DEFAULT_MAX_ENTRIES = 128;
    public static final int ABSOLUTE_MAX_ENTRIES = 2048;
    public static final int DEFAULT_MAX_POPULATED_CELLS = 4096;
    public static final int SIMPLE_VALUE_FIELD_ID = 1;

    private static final Pattern FIELD_NAME = Pattern.compile("^[A-Za-z0-9_]{1,40}$");
    private static final Set<String> RESERVED_NAMES = Set.of("found", "index", "length", "occupied");

    private final WiredArrayFormat format;
    private final WiredArrayMode mode;
    private final int maxEntries;
    private final int nextFieldId;
    private final List<WiredArrayFieldDefinition> fields;
    private final Map<Integer, WiredArrayFieldDefinition> fieldsById;

    private WiredArrayDefinition(
            WiredArrayFormat format,
            WiredArrayMode mode,
            int maxEntries,
            int nextFieldId,
            List<WiredArrayFieldDefinition> fields) {
        this.format = format;
        this.mode = mode;
        this.maxEntries = maxEntries;
        this.nextFieldId = nextFieldId;
        this.fields = Collections.unmodifiableList(new ArrayList<>(fields));
        Map<Integer, WiredArrayFieldDefinition> byId = new HashMap<>();
        for (WiredArrayFieldDefinition field : fields) {
            byId.put(field.getId(), field);
        }
        this.fieldsById = Collections.unmodifiableMap(byId);
    }

    public static WiredArrayDefinition fromData(WiredVariableDefinitionData data, int serverMaximum) {
        if (data == null || !data.isArray()) {
            return null;
        }

        int maximum = Math.max(1, Math.min(ABSOLUTE_MAX_ENTRIES, serverMaximum));
        if (data.maxEntries < 1 || data.maxEntries > maximum) {
            throw new IllegalArgumentException("Maximum entries must be between 1 and " + maximum + ".");
        }
        if (data.schemaVersion != 0 && data.schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported array schema version.");
        }

        WiredArrayFormat format = WiredArrayFormat.fromWireName(data.arrayFormat);
        WiredArrayMode mode = WiredArrayMode.fromWireName(data.arrayMode);
        if (format == WiredArrayFormat.SIMPLE) {
            return new WiredArrayDefinition(
                    format,
                    mode,
                    data.maxEntries,
                    2,
                    List.of(new WiredArrayFieldDefinition(SIMPLE_VALUE_FIELD_ID, "value", 0)));
        }

        List<WiredArrayFieldDefinition> requested =
                data.fields == null ? new ArrayList<>() : new ArrayList<>(data.fields);
        if (requested.isEmpty() || requested.size() > MAX_FIELDS) {
            throw new IllegalArgumentException("Record arrays require between 1 and " + MAX_FIELDS + " fields.");
        }

        requested.sort(Comparator.comparingInt(WiredArrayFieldDefinition::getOrder));
        Set<Integer> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        List<WiredArrayFieldDefinition> normalized = new ArrayList<>();
        int greatestId = 0;

        for (int order = 0; order < requested.size(); order++) {
            WiredArrayFieldDefinition field = requested.get(order);
            if (field == null || field.getId() <= 0 || field.getId() > MAX_FIELD_ID || !ids.add(field.getId())) {
                throw new IllegalArgumentException(
                        "Array field IDs must be unique and between 1 and " + MAX_FIELD_ID + ".");
            }
            String name = field.getName() == null ? "" : field.getName().trim();
            String comparisonName = name.toLowerCase(Locale.ROOT);
            if (!FIELD_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Array field names must be 1-40 letters, numbers, or underscores.");
            }
            if (RESERVED_NAMES.contains(comparisonName)) {
                throw new IllegalArgumentException("Array field name '" + name + "' is reserved.");
            }
            if (!names.add(comparisonName)) {
                throw new IllegalArgumentException("Array field names must be unique.");
            }
            greatestId = Math.max(greatestId, field.getId());
            normalized.add(new WiredArrayFieldDefinition(field.getId(), name, order));
        }

        if (greatestId == MAX_FIELD_ID
                || data.nextFieldId > MAX_FIELD_ID
                || (data.nextFieldId > 0 && data.nextFieldId <= greatestId)) {
            throw new IllegalArgumentException("Array field ID sequence is invalid.");
        }
        int nextFieldId = data.nextFieldId <= 0 ? greatestId + 1 : data.nextFieldId;
        return new WiredArrayDefinition(format, mode, data.maxEntries, nextFieldId, normalized);
    }

    public WiredArrayFormat getFormat() {
        return this.format;
    }

    public WiredArrayMode getMode() {
        return this.mode;
    }

    public int getMaxEntries() {
        return this.maxEntries;
    }

    public int getNextFieldId() {
        return this.nextFieldId;
    }

    public List<WiredArrayFieldDefinition> getFields() {
        return this.fields;
    }

    public WiredArrayFieldDefinition getField(int fieldId) {
        return this.fieldsById.get(fieldId);
    }

    public boolean isShapeCompatible(WiredArrayDefinition replacement) {
        return replacement != null && this.format == replacement.format && this.mode == replacement.mode;
    }

    public boolean removesFieldsComparedWith(WiredArrayDefinition replacement) {
        if (replacement == null) {
            return !this.fields.isEmpty();
        }
        for (Integer fieldId : this.fieldsById.keySet()) {
            if (!replacement.fieldsById.containsKey(fieldId)) {
                return true;
            }
        }
        return false;
    }
}
