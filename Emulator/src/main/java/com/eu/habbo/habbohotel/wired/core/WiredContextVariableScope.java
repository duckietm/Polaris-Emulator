package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayCaptureSnapshot;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayMutationResult;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayNumericOperation;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayRuntimeSupport;
import com.eu.habbo.habbohotel.wired.arrays.WiredArraySettings;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayStructuralOperation;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayValue;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayView;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class WiredContextVariableScope {
    private final LinkedHashMap<Integer, VariableAssignment> assignments;
    private final LinkedHashMap<Integer, WiredArrayValue> arrays;
    private final LinkedHashMap<String, WiredArrayCaptureSnapshot> arrayCaptures;

    public WiredContextVariableScope() {
        this.assignments = new LinkedHashMap<>();
        this.arrays = new LinkedHashMap<>();
        this.arrayCaptures = new LinkedHashMap<>();
    }

    private WiredContextVariableScope(
            Map<Integer, VariableAssignment> source,
            Map<Integer, WiredArrayValue> sourceArrays,
            Map<String, WiredArrayCaptureSnapshot> sourceCaptures) {
        this.assignments = new LinkedHashMap<>();
        this.arrays = new LinkedHashMap<>();
        this.arrayCaptures = new LinkedHashMap<>();

        if (source == null || source.isEmpty()) {
        } else {
            for (Map.Entry<Integer, VariableAssignment> entry : source.entrySet()) {
                if (entry == null || entry.getKey() == null || entry.getKey() <= 0 || entry.getValue() == null) {
                    continue;
                }

                this.assignments.put(entry.getKey(), entry.getValue().copy());
            }
        }
        if (sourceArrays != null) {
            sourceArrays.forEach((itemId, value) -> {
                if (itemId != null && itemId > 0 && value != null) this.arrays.put(itemId, value.copy());
            });
        }
        if (sourceCaptures != null) this.arrayCaptures.putAll(sourceCaptures);
    }

    public synchronized WiredContextVariableScope copy() {
        return new WiredContextVariableScope(this.assignments, this.arrays, this.arrayCaptures);
    }

    public synchronized WiredArrayValue getArrayValue(int definitionItemId, WiredArrayDefinition definition) {
        if (definitionItemId <= 0 || definition == null) return null;
        WiredArrayValue value = this.arrays.get(definitionItemId);
        return value == null || !matchesDefinition(value.getDefinition(), definition) ? null : value.copy();
    }

    public synchronized WiredArrayView getArrayView(int definitionItemId, WiredArrayDefinition definition) {
        if (definitionItemId <= 0 || definition == null) return null;
        WiredArrayValue value = this.arrays.get(definitionItemId);
        return value == null || !matchesDefinition(value.getDefinition(), definition) ? null : value;
    }

    public synchronized WiredArrayMutationResult giveArray(
            int definitionItemId, WiredArrayDefinition definition, boolean overrideExisting) {
        if (definitionItemId <= 0 || definition == null) return WiredArrayMutationResult.MISSING_OWNER;
        WiredArrayValue current = this.arrays.get(definitionItemId);
        if (current != null && (!overrideExisting || current.isEmpty())) {
            return WiredArrayMutationResult.NO_CHANGE;
        }
        this.arrays.put(
                definitionItemId, WiredArrayValue.empty(definition, WiredArraySettings.maxPopulatedCellsPerOwner()));
        return WiredArrayMutationResult.SUCCESS;
    }

    public synchronized boolean removeArray(int definitionItemId) {
        return definitionItemId > 0 && this.arrays.remove(definitionItemId) != null;
    }

    public synchronized boolean hasArray(int definitionItemId) {
        return definitionItemId > 0 && this.arrays.containsKey(definitionItemId);
    }

    public synchronized WiredArrayMutationResult mutateArray(
            int definitionItemId,
            WiredArrayDefinition definition,
            WiredArrayStructuralOperation operation,
            int firstIndex,
            int secondIndex,
            Map<Integer, Long> entryValues) {
        WiredArrayValue current = this.arrays.get(definitionItemId);
        if (current == null) return WiredArrayMutationResult.MISSING_OWNER;
        WiredArrayValue candidate = current.copy();
        WiredArrayMutationResult result = candidate.apply(operation, firstIndex, secondIndex, entryValues);
        if (result == WiredArrayMutationResult.SUCCESS) this.arrays.put(definitionItemId, candidate);
        return result;
    }

    public synchronized WiredArrayValue.FieldMutation mutateArrayField(
            int definitionItemId, int index, int fieldId, WiredArrayNumericOperation operation, long reference) {
        WiredArrayValue current = this.arrays.get(definitionItemId);
        if (current == null) {
            return new WiredArrayValue.FieldMutation(WiredArrayMutationResult.MISSING_OWNER, 0L, 0L, false);
        }
        WiredArrayValue candidate = current.copy();
        WiredArrayValue.FieldMutation result = candidate.mutateField(index, fieldId, operation, reference);
        if (result.changed()) this.arrays.put(definitionItemId, candidate);
        return result;
    }

    public synchronized boolean hasArrayValues(int definitionItemId) {
        WiredArrayValue value = this.arrays.get(definitionItemId);
        return value != null && !value.isEmpty();
    }

    public synchronized void publishArrayCapture(String alias, WiredArrayCaptureSnapshot capture) {
        if (alias == null || alias.isBlank() || capture == null) return;
        this.arrayCaptures.put(alias.toLowerCase(Locale.ROOT), capture);
    }

    public synchronized Long readArrayCapture(String path) {
        if (!WiredArrayRuntimeSupport.isValidCaptureProjectionPath(path)) {
            return null;
        }
        String normalized = path.trim();
        if (normalized.regionMatches(true, 0, "@array.", 0, 7)) normalized = normalized.substring(7);
        String[] parts = normalized.split("\\.", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) return null;
        WiredArrayCaptureSnapshot capture = this.arrayCaptures.get(parts[0].toLowerCase(Locale.ROOT));
        return capture == null ? null : capture.read(parts[1]);
    }

    public synchronized boolean hasVariable(int definitionItemId) {
        return definitionItemId > 0 && this.assignments.containsKey(definitionItemId);
    }

    public synchronized Integer getValue(int definitionItemId) {
        VariableAssignment assignment = this.assignments.get(definitionItemId);
        return assignment != null ? assignment.getValue() : null;
    }

    public synchronized int getCreatedAt(int definitionItemId) {
        VariableAssignment assignment = this.assignments.get(definitionItemId);
        return assignment != null ? assignment.getCreatedAt() : 0;
    }

    public synchronized int getUpdatedAt(int definitionItemId) {
        VariableAssignment assignment = this.assignments.get(definitionItemId);
        return assignment != null ? assignment.getUpdatedAt() : 0;
    }

    public synchronized boolean assignValue(int definitionItemId, Integer value, boolean overrideExisting) {
        if (definitionItemId <= 0) {
            return false;
        }

        VariableAssignment existingAssignment = this.assignments.get(definitionItemId);

        if (existingAssignment != null && !overrideExisting) {
            return false;
        }

        int now = Emulator.getIntUnixTimestamp();

        if (existingAssignment == null || overrideExisting) {
            this.assignments.put(definitionItemId, new VariableAssignment(value, now, now));
            return true;
        }

        return false;
    }

    public synchronized boolean updateValue(int definitionItemId, Integer value) {
        if (definitionItemId <= 0) {
            return false;
        }

        VariableAssignment assignment = this.assignments.get(definitionItemId);
        if (assignment == null) {
            return false;
        }

        if ((assignment.getValue() == null && value == null)
                || (assignment.getValue() != null && assignment.getValue().equals(value))) {
            return false;
        }

        assignment.setValue(value, Emulator.getIntUnixTimestamp());
        return true;
    }

    public synchronized boolean removeValue(int definitionItemId) {
        if (definitionItemId <= 0) {
            return false;
        }

        return this.assignments.remove(definitionItemId) != null;
    }

    private static boolean matchesDefinition(WiredArrayDefinition current, WiredArrayDefinition expected) {
        return current != null
                && current.getFormat() == expected.getFormat()
                && current.getMode() == expected.getMode()
                && current.getMaxEntries() == expected.getMaxEntries()
                && current.getFields().equals(expected.getFields());
    }

    public static final class VariableAssignment {
        private Integer value;
        private final int createdAt;
        private int updatedAt;

        public VariableAssignment(Integer value, int createdAt, int updatedAt) {
            this.value = value;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public Integer getValue() {
            return this.value;
        }

        public int getCreatedAt() {
            return this.createdAt;
        }

        public int getUpdatedAt() {
            return this.updatedAt;
        }

        public void setValue(Integer value, int updatedAt) {
            this.value = value;
            this.updatedAt = updatedAt;
        }

        private VariableAssignment copy() {
            return new VariableAssignment(this.value, this.createdAt, this.updatedAt);
        }
    }
}
