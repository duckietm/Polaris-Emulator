package com.eu.habbo.habbohotel.wired.arrays;

public enum WiredArrayStructuralOperation {
    APPEND(0, true, false, true),
    INSERT(1, true, false, true),
    SET_ENTRY(2, true, true, true),
    REMOVE(3, true, false, false),
    REMOVE_FIRST(4, true, false, false),
    REMOVE_LAST(5, true, false, false),
    SWAP(6, true, true, false),
    MOVE(7, true, false, false),
    CLEAR(8, true, true, false),
    CLEAR_SLOT(9, false, true, false),
    SHUFFLE(10, true, false, false);

    private final int code;
    private final boolean list;
    private final boolean slots;
    private final boolean entryValues;

    WiredArrayStructuralOperation(int code, boolean list, boolean slots, boolean entryValues) {
        this.code = code;
        this.list = list;
        this.slots = slots;
        this.entryValues = entryValues;
    }

    public int code() {
        return this.code;
    }

    public boolean supports(WiredArrayMode mode) {
        return mode == WiredArrayMode.LIST ? this.list : this.slots;
    }

    public boolean requiresEntryValues() {
        return this.entryValues;
    }

    public boolean requiresFirstIndex() {
        return this == INSERT
                || this == SET_ENTRY
                || this == REMOVE
                || this == CLEAR_SLOT
                || this == SWAP
                || this == MOVE;
    }

    public boolean requiresSecondIndex() {
        return this == SWAP || this == MOVE;
    }

    public static WiredArrayStructuralOperation fromCode(int code) {
        for (WiredArrayStructuralOperation operation : values()) {
            if (operation.code == code) {
                return operation;
            }
        }
        return null;
    }
}
