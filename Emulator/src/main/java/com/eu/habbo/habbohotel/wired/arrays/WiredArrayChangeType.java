package com.eu.habbo.habbohotel.wired.arrays;

/** Stable array-change codes shared with the Variable Changed editor. */
public final class WiredArrayChangeType {
    public static final int ANY = 0;
    public static final int ENTRY_APPENDED = 1;
    public static final int ENTRY_INSERTED = 2;
    public static final int ENTRY_REMOVED = 3;
    public static final int INDEX_CLEARED = 4;
    public static final int ENTRY_REPLACED = 5;
    public static final int ENTRY_MOVED = 6;
    public static final int FIELD_VALUE_CHANGED = 7;
    public static final int LENGTH_CHANGED = 8;
    public static final int ARRAY_CLEARED = 9;
    public static final int ARRAY_CREATED = 10;
    public static final int ENTRIES_SWAPPED = 11;
    public static final int ARRAY_SHUFFLED = 12;

    private WiredArrayChangeType() {}

    public static int from(WiredArrayStructuralOperation operation) {
        return switch (operation) {
            case APPEND -> ENTRY_APPENDED;
            case INSERT -> ENTRY_INSERTED;
            case REMOVE, REMOVE_FIRST, REMOVE_LAST -> ENTRY_REMOVED;
            case CLEAR_SLOT -> INDEX_CLEARED;
            case SET_ENTRY -> ENTRY_REPLACED;
            case MOVE -> ENTRY_MOVED;
            case SWAP -> ENTRIES_SWAPPED;
            case CLEAR -> ARRAY_CLEARED;
            case SHUFFLE -> ARRAY_SHUFFLED;
        };
    }
}
