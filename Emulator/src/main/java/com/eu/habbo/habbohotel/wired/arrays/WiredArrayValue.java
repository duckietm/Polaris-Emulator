package com.eu.habbo.habbohotel.wired.arrays;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.random.RandomGenerator;

/** Copy-on-write array value. Slots are sparse; lists always have entries below logical length. */
public final class WiredArrayValue {
    private final WiredArrayDefinition definition;
    private final int populatedCellLimit;
    private int logicalLength;
    private final NavigableMap<Integer, WiredArrayEntry> entries;

    private WiredArrayValue(
            WiredArrayDefinition definition, int logicalLength, int populatedCellLimit, boolean initializeListEntries) {
        if (definition == null) {
            throw new IllegalArgumentException("Array definition is required.");
        }
        if (logicalLength < 0 || logicalLength > definition.getMaxEntries()) {
            throw new IllegalArgumentException("Invalid array length.");
        }
        if (definition.getMode() == WiredArrayMode.SLOTS && logicalLength != 0) {
            throw new IllegalArgumentException("Slots arrays do not use logical list length.");
        }
        this.definition = definition;
        this.logicalLength = logicalLength;
        this.populatedCellLimit = Math.max(1, populatedCellLimit);
        this.entries = new TreeMap<>();
        if (initializeListEntries && definition.getMode() == WiredArrayMode.LIST) {
            for (int index = 0; index < logicalLength; index++) {
                this.entries.put(index, WiredArrayEntry.fromValues(definition, Collections.emptyMap()));
            }
        }
        this.validateCellLimit(this.entries.size());
    }

    public static WiredArrayValue empty(WiredArrayDefinition definition, int populatedCellLimit) {
        return new WiredArrayValue(definition, 0, populatedCellLimit, false);
    }

    public static WiredArrayValue loaded(
            WiredArrayDefinition definition,
            int logicalLength,
            int populatedCellLimit,
            Map<Integer, Map<Integer, Long>> storedEntries) {
        WiredArrayValue value = new WiredArrayValue(definition, logicalLength, populatedCellLimit, true);
        if (storedEntries == null) {
            return value;
        }
        for (Map.Entry<Integer, Map<Integer, Long>> stored : storedEntries.entrySet()) {
            Integer index = stored.getKey();
            if (index == null || index < 0 || index >= definition.getMaxEntries()) {
                throw new IllegalArgumentException("Stored array index is outside the definition.");
            }
            if (definition.getMode() == WiredArrayMode.LIST && index >= logicalLength) {
                throw new IllegalArgumentException("Stored list index is outside its logical length.");
            }
            value.entries.put(index, WiredArrayEntry.fromValues(definition, stored.getValue()));
        }
        value.validateCellLimit(value.entries.size());
        return value;
    }

    public WiredArrayMutationResult apply(
            WiredArrayStructuralOperation operation, int firstIndex, int secondIndex, Map<Integer, Long> entryValues) {
        return this.apply(operation, firstIndex, secondIndex, entryValues, RandomGenerator.getDefault());
    }

    WiredArrayMutationResult apply(
            WiredArrayStructuralOperation operation,
            int firstIndex,
            int secondIndex,
            Map<Integer, Long> entryValues,
            RandomGenerator random) {
        if (operation == null) {
            return WiredArrayMutationResult.INVALID_OPERATION;
        }
        if (!operation.supports(this.definition.getMode())) {
            return WiredArrayMutationResult.WRONG_ARRAY_MODE;
        }

        WiredArrayEntry replacement = null;
        if (operation.requiresEntryValues()) {
            WiredArrayMutationResult validation = this.validateCompleteEntry(entryValues);
            if (validation != WiredArrayMutationResult.SUCCESS) {
                return validation;
            }
            replacement = WiredArrayEntry.fromValues(this.definition, entryValues);
        }

        if (operation == WiredArrayStructuralOperation.CLEAR) {
            if (this.entries.isEmpty() && this.logicalLength == 0) {
                return WiredArrayMutationResult.NO_CHANGE;
            }
            this.entries.clear();
            this.logicalLength = 0;
            return WiredArrayMutationResult.SUCCESS;
        }

        return this.definition.getMode() == WiredArrayMode.LIST
                ? this.applyList(operation, firstIndex, secondIndex, replacement, random)
                : this.applySlots(operation, firstIndex, secondIndex, replacement);
    }

    public WiredArrayDefinition getDefinition() {
        return this.definition;
    }

    public int getLogicalLength() {
        return this.definition.getMode() == WiredArrayMode.LIST ? this.logicalLength : 0;
    }

    public int getOccupiedCount() {
        return this.entries.size();
    }

    public int getLengthForCondition() {
        return this.definition.getMode() == WiredArrayMode.LIST ? this.logicalLength : this.entries.size();
    }

    public int getAvailableIndexes() {
        return this.definition.getMaxEntries() - this.getLengthForCondition();
    }

    public boolean isFull() {
        return this.getLengthForCondition() >= this.definition.getMaxEntries();
    }

    public boolean isEmpty() {
        return this.getLengthForCondition() == 0;
    }

    public WiredArrayEntry getEntry(int index) {
        return this.entries.get(index);
    }

    public Long readField(int index, int fieldId) {
        if (this.definition.getField(fieldId) == null || index < 0 || index >= this.definition.getMaxEntries()) {
            return null;
        }
        WiredArrayEntry entry = this.entries.get(index);
        return entry == null ? null : entry.getValue(fieldId);
    }

    /** Applies one numeric field operation to this unpublished candidate value. */
    public FieldMutation mutateField(int index, int fieldId, WiredArrayNumericOperation operation, long reference) {
        if (operation == null) {
            return FieldMutation.failed(WiredArrayMutationResult.INVALID_OPERATION);
        }
        if (this.definition.getField(fieldId) == null) {
            return FieldMutation.failed(WiredArrayMutationResult.UNKNOWN_FIELD);
        }
        if (!this.isCapacityIndex(index)) {
            return FieldMutation.failed(WiredArrayMutationResult.INVALID_INDEX);
        }

        WiredArrayEntry entry = this.entries.get(index);
        boolean created = entry == null;
        if (created) {
            if (operation != WiredArrayNumericOperation.ASSIGN) {
                return FieldMutation.failed(WiredArrayMutationResult.MISSING_ENTRY);
            }
            if (this.definition.getMode() == WiredArrayMode.LIST && index != this.logicalLength) {
                return FieldMutation.failed(WiredArrayMutationResult.MISSING_ENTRY);
            }
            WiredArrayMutationResult quota = this.canAddEntry();
            if (quota != WiredArrayMutationResult.SUCCESS) return FieldMutation.failed(quota);
            entry = WiredArrayEntry.fromValues(this.definition, Collections.emptyMap());
        }

        long previous = entry.getValue(fieldId);
        long next = operation.apply(previous, reference);
        if (!created && previous == next) {
            return new FieldMutation(WiredArrayMutationResult.NO_CHANGE, previous, next, false);
        }

        Map<Integer, Long> values = new LinkedHashMap<>(entry.valuesByFieldId());
        values.put(fieldId, next);
        this.entries.put(index, WiredArrayEntry.fromValues(this.definition, values));
        if (created && this.definition.getMode() == WiredArrayMode.LIST) this.logicalLength++;
        return new FieldMutation(WiredArrayMutationResult.SUCCESS, previous, next, created);
    }

    public Map<Integer, WiredArrayEntry> entriesSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.entries));
    }

    public WiredArrayValue copy() {
        WiredArrayValue copy =
                new WiredArrayValue(this.definition, this.getLogicalLength(), this.populatedCellLimit, false);
        copy.entries.putAll(this.entries);
        return copy;
    }

    public WiredArrayValue redefined(WiredArrayDefinition replacement) {
        if (!this.definition.isShapeCompatible(replacement)
                || replacement.getMaxEntries() < this.definition.getMaxEntries()) {
            throw new IllegalArgumentException("Array value cannot be reshaped in place.");
        }
        Map<Integer, Map<Integer, Long>> retained = new LinkedHashMap<>();
        for (Map.Entry<Integer, WiredArrayEntry> entry : this.entries.entrySet()) {
            retained.put(entry.getKey(), entry.getValue().valuesByFieldId());
        }
        return loaded(replacement, this.getLogicalLength(), this.populatedCellLimit, retained);
    }

    private WiredArrayMutationResult applyList(
            WiredArrayStructuralOperation operation,
            int firstIndex,
            int secondIndex,
            WiredArrayEntry replacement,
            RandomGenerator random) {
        switch (operation) {
            case APPEND -> {
                WiredArrayMutationResult quota = this.canAddEntry();
                if (quota != WiredArrayMutationResult.SUCCESS) return quota;
                this.entries.put(this.logicalLength++, replacement);
                return WiredArrayMutationResult.SUCCESS;
            }
            case INSERT -> {
                if (firstIndex < 0 || firstIndex > this.logicalLength) return WiredArrayMutationResult.INVALID_INDEX;
                WiredArrayMutationResult quota = this.canAddEntry();
                if (quota != WiredArrayMutationResult.SUCCESS) return quota;
                for (int index = this.logicalLength; index > firstIndex; index--) {
                    this.entries.put(index, this.entries.get(index - 1));
                }
                this.entries.put(firstIndex, replacement);
                this.logicalLength++;
                return WiredArrayMutationResult.SUCCESS;
            }
            case SET_ENTRY -> {
                if (!this.isListIndex(firstIndex)) return WiredArrayMutationResult.MISSING_ENTRY;
                this.entries.put(firstIndex, replacement);
                return WiredArrayMutationResult.SUCCESS;
            }
            case REMOVE -> {
                if (!this.isListIndex(firstIndex)) return WiredArrayMutationResult.MISSING_ENTRY;
                this.removeListEntry(firstIndex);
                return WiredArrayMutationResult.SUCCESS;
            }
            case REMOVE_FIRST -> {
                if (this.logicalLength == 0) return WiredArrayMutationResult.ARRAY_EMPTY;
                this.removeListEntry(0);
                return WiredArrayMutationResult.SUCCESS;
            }
            case REMOVE_LAST -> {
                if (this.logicalLength == 0) return WiredArrayMutationResult.ARRAY_EMPTY;
                this.removeListEntry(this.logicalLength - 1);
                return WiredArrayMutationResult.SUCCESS;
            }
            case SWAP -> {
                if (!this.isListIndex(firstIndex) || !this.isListIndex(secondIndex)) {
                    return WiredArrayMutationResult.MISSING_ENTRY;
                }
                if (firstIndex == secondIndex) return WiredArrayMutationResult.NO_CHANGE;
                WiredArrayEntry first = this.entries.get(firstIndex);
                this.entries.put(firstIndex, this.entries.get(secondIndex));
                this.entries.put(secondIndex, first);
                return WiredArrayMutationResult.SUCCESS;
            }
            case MOVE -> {
                if (!this.isListIndex(firstIndex) || !this.isListIndex(secondIndex)) {
                    return WiredArrayMutationResult.MISSING_ENTRY;
                }
                if (firstIndex == secondIndex) return WiredArrayMutationResult.NO_CHANGE;
                this.moveListEntry(firstIndex, secondIndex);
                return WiredArrayMutationResult.SUCCESS;
            }
            case SHUFFLE -> {
                if (this.logicalLength < 2) return WiredArrayMutationResult.NO_CHANGE;
                List<WiredArrayEntry> original = new ArrayList<>(this.entries.values());
                List<WiredArrayEntry> shuffled = new ArrayList<>(original);
                Collections.shuffle(shuffled, new java.util.Random(random.nextLong()));
                if (shuffled.equals(original)) Collections.rotate(shuffled, 1);
                for (int index = 0; index < shuffled.size(); index++) {
                    this.entries.put(index, shuffled.get(index));
                }
                return WiredArrayMutationResult.SUCCESS;
            }
            default -> {
                return WiredArrayMutationResult.WRONG_ARRAY_MODE;
            }
        }
    }

    private WiredArrayMutationResult applySlots(
            WiredArrayStructuralOperation operation, int firstIndex, int secondIndex, WiredArrayEntry replacement) {
        if (operation == WiredArrayStructuralOperation.SET_ENTRY) {
            if (!this.isCapacityIndex(firstIndex)) return WiredArrayMutationResult.INVALID_INDEX;
            if (!this.entries.containsKey(firstIndex)) {
                WiredArrayMutationResult quota = this.canAddEntry();
                if (quota != WiredArrayMutationResult.SUCCESS) return quota;
            }
            this.entries.put(firstIndex, replacement);
            return WiredArrayMutationResult.SUCCESS;
        }
        if (operation == WiredArrayStructuralOperation.CLEAR_SLOT) {
            if (!this.isCapacityIndex(firstIndex)) return WiredArrayMutationResult.INVALID_INDEX;
            return this.entries.remove(firstIndex) == null
                    ? WiredArrayMutationResult.EMPTY_SLOT
                    : WiredArrayMutationResult.SUCCESS;
        }
        if (operation == WiredArrayStructuralOperation.SWAP) {
            if (!this.isCapacityIndex(firstIndex) || !this.isCapacityIndex(secondIndex)) {
                return WiredArrayMutationResult.INVALID_INDEX;
            }
            if (firstIndex == secondIndex) return WiredArrayMutationResult.NO_CHANGE;
            WiredArrayEntry first = this.entries.get(firstIndex);
            WiredArrayEntry second = this.entries.get(secondIndex);
            if (first == null && second == null) return WiredArrayMutationResult.NO_CHANGE;
            if (second == null) this.entries.remove(firstIndex);
            else this.entries.put(firstIndex, second);
            if (first == null) this.entries.remove(secondIndex);
            else this.entries.put(secondIndex, first);
            return WiredArrayMutationResult.SUCCESS;
        }
        return WiredArrayMutationResult.WRONG_ARRAY_MODE;
    }

    private WiredArrayMutationResult validateCompleteEntry(Map<Integer, Long> values) {
        if (values == null || values.size() != this.definition.getFields().size()) {
            return WiredArrayMutationResult.MISSING_FIELD;
        }
        for (Map.Entry<Integer, Long> value : values.entrySet()) {
            if (value.getKey() == null || this.definition.getField(value.getKey()) == null) {
                return WiredArrayMutationResult.UNKNOWN_FIELD;
            }
            if (value.getValue() == null) return WiredArrayMutationResult.MISSING_FIELD;
        }
        return WiredArrayMutationResult.SUCCESS;
    }

    private WiredArrayMutationResult canAddEntry() {
        if (this.entries.size() >= this.definition.getMaxEntries()) {
            return WiredArrayMutationResult.ARRAY_FULL;
        }
        long cells =
                (long) (this.entries.size() + 1) * this.definition.getFields().size();
        return cells <= this.populatedCellLimit
                ? WiredArrayMutationResult.SUCCESS
                : WiredArrayMutationResult.POPULATED_CELL_LIMIT;
    }

    private void validateCellLimit(int entryCount) {
        if ((long) entryCount * this.definition.getFields().size() > this.populatedCellLimit) {
            throw new IllegalArgumentException("Array populated-data safety limit exceeded.");
        }
    }

    private boolean isListIndex(int index) {
        return index >= 0 && index < this.logicalLength && this.entries.containsKey(index);
    }

    private boolean isCapacityIndex(int index) {
        return index >= 0 && index < this.definition.getMaxEntries();
    }

    private void removeListEntry(int index) {
        for (int current = index; current < this.logicalLength - 1; current++) {
            this.entries.put(current, this.entries.get(current + 1));
        }
        this.entries.remove(--this.logicalLength);
    }

    private void moveListEntry(int sourceIndex, int destinationIndex) {
        WiredArrayEntry moved = this.entries.get(sourceIndex);
        if (sourceIndex < destinationIndex) {
            for (int index = sourceIndex; index < destinationIndex; index++) {
                this.entries.put(index, this.entries.get(index + 1));
            }
        } else {
            for (int index = sourceIndex; index > destinationIndex; index--) {
                this.entries.put(index, this.entries.get(index - 1));
            }
        }
        this.entries.put(destinationIndex, moved);
    }

    public record FieldMutation(
            WiredArrayMutationResult result, long previousValue, long currentValue, boolean created) {
        private static FieldMutation failed(WiredArrayMutationResult result) {
            return new FieldMutation(result, 0L, 0L, false);
        }

        public boolean changed() {
            return this.result == WiredArrayMutationResult.SUCCESS;
        }
    }
}
