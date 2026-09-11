package com.eu.habbo.habbohotel.rooms;

public class WiredVariableDefinitionInfo {
    private final int itemId;
    private final String name;
    private final boolean hasValue;
    private final int availability;
    private final boolean textConnected;
    private final boolean readOnly;
    private final boolean array;

    public WiredVariableDefinitionInfo(
            int itemId, String name, boolean hasValue, int availability, boolean textConnected, boolean readOnly) {
        this(itemId, name, hasValue, availability, textConnected, readOnly, false);
    }

    public WiredVariableDefinitionInfo(
            int itemId,
            String name,
            boolean hasValue,
            int availability,
            boolean textConnected,
            boolean readOnly,
            boolean array) {
        this.itemId = itemId;
        this.name = name;
        this.hasValue = hasValue;
        this.availability = availability;
        this.textConnected = textConnected;
        this.readOnly = readOnly;
        this.array = array;
    }

    public int getItemId() {
        return this.itemId;
    }

    public String getName() {
        return this.name;
    }

    public boolean hasValue() {
        return this.hasValue;
    }

    public int getAvailability() {
        return this.availability;
    }

    public boolean isTextConnected() {
        return this.textConnected;
    }

    public boolean isReadOnly() {
        return this.readOnly;
    }

    public boolean isArray() {
        return this.array;
    }
}
