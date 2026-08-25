package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;

import java.util.LinkedHashMap;
import java.util.Map;

public final class WiredContextVariableScope {
    private final LinkedHashMap<Integer, VariableAssignment> assignments;

    public WiredContextVariableScope() {
        this.assignments = new LinkedHashMap<>();
    }

    private WiredContextVariableScope(Map<Integer, VariableAssignment> source) {
        this.assignments = new LinkedHashMap<>();

        if (source == null || source.isEmpty()) {
            return;
        }

        for (Map.Entry<Integer, VariableAssignment> entry : source.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getKey() <= 0 || entry.getValue() == null) {
                continue;
            }

            this.assignments.put(entry.getKey(), entry.getValue().copy());
        }
    }

    public WiredContextVariableScope copy() {
        return new WiredContextVariableScope(this.assignments);
    }

    public boolean hasVariable(int definitionItemId) {
        return definitionItemId > 0 && this.assignments.containsKey(definitionItemId);
    }

    public Integer getValue(int definitionItemId) {
        VariableAssignment assignment = this.assignments.get(definitionItemId);
        return assignment != null ? assignment.getValue() : null;
    }

    public int getCreatedAt(int definitionItemId) {
        VariableAssignment assignment = this.assignments.get(definitionItemId);
        return assignment != null ? assignment.getCreatedAt() : 0;
    }

    public int getUpdatedAt(int definitionItemId) {
        VariableAssignment assignment = this.assignments.get(definitionItemId);
        return assignment != null ? assignment.getUpdatedAt() : 0;
    }

    public boolean assignValue(int definitionItemId, Integer value, boolean overrideExisting) {
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

    public boolean updateValue(int definitionItemId, Integer value) {
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

    public boolean removeValue(int definitionItemId) {
        if (definitionItemId <= 0) {
            return false;
        }

        return this.assignments.remove(definitionItemId) != null;
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
