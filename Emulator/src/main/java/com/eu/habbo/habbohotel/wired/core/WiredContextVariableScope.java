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

/**
 * The context variables of one stack execution. A stack that a signal or a stack call starts works in
 * a {@link #child() child scope} of its sender's, as in Habbo: reads and changes reach the variable in
 * the nearest scope that holds it, so the sender (and every other stack it started) sees them, while a
 * give - a new variable, or one that overrides an existing one - creates the variable in the child,
 * where it hides the sender's.
 */
public final class WiredContextVariableScope {
    private final LinkedHashMap<Integer, VariableAssignment> assignments;
    private final LinkedHashMap<Integer, WiredArrayValue> arrays;
    private final LinkedHashMap<String, WiredArrayCaptureSnapshot> arrayCaptures;
    /** The sender's scope, or null for the scope of an execution nothing started. */
    private final WiredContextVariableScope parent;

    public WiredContextVariableScope() {
        this.assignments = new LinkedHashMap<>();
        this.arrays = new LinkedHashMap<>();
        this.arrayCaptures = new LinkedHashMap<>();
        this.parent = null;
    }

    private WiredContextVariableScope(WiredContextVariableScope parent) {
        this.assignments = new LinkedHashMap<>();
        this.arrays = new LinkedHashMap<>();
        this.arrayCaptures = new LinkedHashMap<>();
        this.parent = parent;
    }

    private WiredContextVariableScope(
            Map<Integer, VariableAssignment> source,
            Map<Integer, WiredArrayValue> sourceArrays,
            Map<String, WiredArrayCaptureSnapshot> sourceCaptures) {
        this.assignments = new LinkedHashMap<>();
        this.arrays = new LinkedHashMap<>();
        this.arrayCaptures = new LinkedHashMap<>();
        this.parent = null;

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

    /** A scope inside this one, for a stack this execution starts (a signal or a stack call). */
    public WiredContextVariableScope child() {
        return new WiredContextVariableScope(this);
    }

    /** The sender's scope, or null. */
    public WiredContextVariableScope parent() {
        return this.parent;
    }

    /**
     * A detached copy of everything this scope sees, the sender's variables included, nearest first.
     * A delayed execution keeps one, so what it changes later stays its own.
     */
    public WiredContextVariableScope copy() {
        java.util.ArrayDeque<WiredContextVariableScope> chain = new java.util.ArrayDeque<>();
        for (WiredContextVariableScope scope = this; scope != null; scope = scope.parent) chain.push(scope);

        Map<Integer, VariableAssignment> assignments = new LinkedHashMap<>();
        Map<Integer, WiredArrayValue> arrays = new LinkedHashMap<>();
        Map<String, WiredArrayCaptureSnapshot> captures = new LinkedHashMap<>();
        // Outermost first, so a nearer scope's variable replaces the one it hides.
        for (WiredContextVariableScope scope : chain) {
            synchronized (scope) {
                assignments.putAll(scope.assignments);
                arrays.putAll(scope.arrays);
                captures.putAll(scope.arrayCaptures);
            }
        }
        return new WiredContextVariableScope(assignments, arrays, captures);
    }

    /** The scope in this chain that holds the variable, nearest first, or null. */
    private WiredContextVariableScope variableOwner(int definitionItemId) {
        for (WiredContextVariableScope scope = this; scope != null; scope = scope.parent) {
            synchronized (scope) {
                if (scope.assignments.containsKey(definitionItemId)) return scope;
            }
        }
        return null;
    }

    /** The scope in this chain that holds the array, nearest first, or null. */
    private WiredContextVariableScope arrayOwner(int definitionItemId) {
        for (WiredContextVariableScope scope = this; scope != null; scope = scope.parent) {
            synchronized (scope) {
                if (scope.arrays.containsKey(definitionItemId)) return scope;
            }
        }
        return null;
    }

    private VariableAssignment assignment(int definitionItemId) {
        WiredContextVariableScope owner = this.variableOwner(definitionItemId);
        if (owner == null) return null;
        synchronized (owner) {
            return owner.assignments.get(definitionItemId);
        }
    }

    private WiredArrayValue array(int definitionItemId) {
        WiredContextVariableScope owner = this.arrayOwner(definitionItemId);
        if (owner == null) return null;
        synchronized (owner) {
            return owner.arrays.get(definitionItemId);
        }
    }

    public WiredArrayValue getArrayValue(int definitionItemId, WiredArrayDefinition definition) {
        if (definitionItemId <= 0 || definition == null) return null;
        WiredArrayValue value = this.array(definitionItemId);
        return value == null || !matchesDefinition(value.getDefinition(), definition) ? null : value.copy();
    }

    public WiredArrayView getArrayView(int definitionItemId, WiredArrayDefinition definition) {
        if (definitionItemId <= 0 || definition == null) return null;
        WiredArrayValue value = this.array(definitionItemId);
        return value == null || !matchesDefinition(value.getDefinition(), definition) ? null : value;
    }

    /** A give creates the array in this scope, where it hides one the sender holds. */
    public WiredArrayMutationResult giveArray(
            int definitionItemId, WiredArrayDefinition definition, boolean overrideExisting) {
        if (definitionItemId <= 0 || definition == null) return WiredArrayMutationResult.MISSING_OWNER;
        WiredArrayValue current = this.array(definitionItemId);
        if (current != null && (!overrideExisting || current.isEmpty())) {
            return WiredArrayMutationResult.NO_CHANGE;
        }
        synchronized (this) {
            this.arrays.put(
                    definitionItemId,
                    WiredArrayValue.empty(definition, WiredArraySettings.maxPopulatedCellsPerOwner()));
        }
        return WiredArrayMutationResult.SUCCESS;
    }

    public boolean removeArray(int definitionItemId) {
        if (definitionItemId <= 0) return false;
        WiredContextVariableScope owner = this.arrayOwner(definitionItemId);
        if (owner == null) return false;
        synchronized (owner) {
            return owner.arrays.remove(definitionItemId) != null;
        }
    }

    public boolean hasArray(int definitionItemId) {
        return definitionItemId > 0 && this.arrayOwner(definitionItemId) != null;
    }

    public WiredArrayMutationResult mutateArray(
            int definitionItemId,
            WiredArrayDefinition definition,
            WiredArrayStructuralOperation operation,
            int firstIndex,
            int secondIndex,
            Map<Integer, Long> entryValues) {
        WiredContextVariableScope owner = this.arrayOwner(definitionItemId);
        if (owner == null) return WiredArrayMutationResult.MISSING_OWNER;
        synchronized (owner) {
            WiredArrayValue current = owner.arrays.get(definitionItemId);
            if (current == null) return WiredArrayMutationResult.MISSING_OWNER;
            WiredArrayValue candidate = current.copy();
            WiredArrayMutationResult result = candidate.apply(operation, firstIndex, secondIndex, entryValues);
            if (result == WiredArrayMutationResult.SUCCESS) owner.arrays.put(definitionItemId, candidate);
            return result;
        }
    }

    public WiredArrayValue.FieldMutation mutateArrayField(
            int definitionItemId, int index, int fieldId, WiredArrayNumericOperation operation, long reference) {
        WiredContextVariableScope owner = this.arrayOwner(definitionItemId);
        if (owner == null) {
            return new WiredArrayValue.FieldMutation(WiredArrayMutationResult.MISSING_OWNER, 0L, 0L, false);
        }
        synchronized (owner) {
            WiredArrayValue current = owner.arrays.get(definitionItemId);
            if (current == null) {
                return new WiredArrayValue.FieldMutation(WiredArrayMutationResult.MISSING_OWNER, 0L, 0L, false);
            }
            WiredArrayValue candidate = current.copy();
            WiredArrayValue.FieldMutation result = candidate.mutateField(index, fieldId, operation, reference);
            if (result.changed()) owner.arrays.put(definitionItemId, candidate);
            return result;
        }
    }

    public boolean hasArrayValues(int definitionItemId) {
        WiredArrayValue value = this.array(definitionItemId);
        return value != null && !value.isEmpty();
    }

    /** A capture belongs to the execution that made it; a started stack still reads its sender's. */
    public synchronized void publishArrayCapture(String alias, WiredArrayCaptureSnapshot capture) {
        if (alias == null || alias.isBlank() || capture == null) return;
        this.arrayCaptures.put(alias.toLowerCase(Locale.ROOT), capture);
    }

    public Long readArrayCapture(String path) {
        return this.readArrayCapture(path, null);
    }

    public WiredArrayCaptureSnapshot getArrayCapture(String alias) {
        if (alias == null) return null;
        String key = alias.toLowerCase(Locale.ROOT);
        for (WiredContextVariableScope scope = this; scope != null; scope = scope.parent) {
            synchronized (scope) {
                WiredArrayCaptureSnapshot capture = scope.arrayCaptures.get(key);
                if (capture != null) return capture;
            }
        }
        return null;
    }

    public Long readArrayCapture(String path, WiredContext ctx) {
        if (!WiredArrayRuntimeSupport.isValidCaptureProjectionPath(path)) {
            return null;
        }
        String normalized = path.trim();
        if (normalized.regionMatches(true, 0, "@array.", 0, 7)) normalized = normalized.substring(7);
        String[] parts = normalized.split("\\.", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) return null;
        WiredArrayCaptureSnapshot capture = this.getArrayCapture(parts[0]);
        return capture == null ? null : capture.read(parts[1], ctx);
    }

    public CapturedFieldMutation mutateCapturedField(
            int definitionItemId, long runtimeId, int fieldId, WiredArrayNumericOperation operation, long reference) {
        WiredArrayValue value = this.array(definitionItemId);
        int index = value == null ? -1 : value.findEntryIndex(runtimeId);
        if (index < 0)
            return new CapturedFieldMutation(
                    -1, new WiredArrayValue.FieldMutation(WiredArrayMutationResult.MISSING_ENTRY, 0, 0, false));
        return new CapturedFieldMutation(
                index, this.mutateArrayField(definitionItemId, index, fieldId, operation, reference));
    }

    public record CapturedFieldMutation(int index, WiredArrayValue.FieldMutation mutation) {}

    public boolean hasVariable(int definitionItemId) {
        return definitionItemId > 0 && this.variableOwner(definitionItemId) != null;
    }

    public Integer getValue(int definitionItemId) {
        VariableAssignment assignment = this.assignment(definitionItemId);
        return assignment != null ? assignment.getValue() : null;
    }

    public int getCreatedAt(int definitionItemId) {
        VariableAssignment assignment = this.assignment(definitionItemId);
        return assignment != null ? assignment.getCreatedAt() : 0;
    }

    public int getUpdatedAt(int definitionItemId) {
        VariableAssignment assignment = this.assignment(definitionItemId);
        return assignment != null ? assignment.getUpdatedAt() : 0;
    }

    /**
     * A give: refused when the variable exists anywhere in the chain and may not be overridden;
     * otherwise the variable is created in this scope, where it hides the sender's.
     */
    public boolean assignValue(int definitionItemId, Integer value, boolean overrideExisting) {
        if (definitionItemId <= 0) {
            return false;
        }

        if (this.variableOwner(definitionItemId) != null && !overrideExisting) {
            return false;
        }

        int now = Emulator.getIntUnixTimestamp();
        synchronized (this) {
            this.assignments.put(definitionItemId, new VariableAssignment(value, now, now));
        }
        return true;
    }

    /** A change: written where the variable lives, so the sender's scope sees it. */
    public boolean updateValue(int definitionItemId, Integer value) {
        if (definitionItemId <= 0) {
            return false;
        }

        WiredContextVariableScope owner = this.variableOwner(definitionItemId);
        if (owner == null) {
            return false;
        }

        synchronized (owner) {
            VariableAssignment assignment = owner.assignments.get(definitionItemId);
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
    }

    /** A removal takes the variable from where it lives. */
    public boolean removeValue(int definitionItemId) {
        if (definitionItemId <= 0) {
            return false;
        }

        WiredContextVariableScope owner = this.variableOwner(definitionItemId);
        if (owner == null) {
            return false;
        }

        synchronized (owner) {
            return owner.assignments.remove(definitionItemId) != null;
        }
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
