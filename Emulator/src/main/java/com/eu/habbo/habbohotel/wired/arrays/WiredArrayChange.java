package com.eu.habbo.habbohotel.wired.arrays;

/** Immutable post-commit mutation metadata for Variable Changed triggers. */
public record WiredArrayChange(
        int changeType,
        int index,
        int sourceIndex,
        int destinationIndex,
        int fieldId,
        Long oldValue,
        Long newValue,
        int oldLength,
        int newLength) {

    public static WiredArrayChange created() {
        return new WiredArrayChange(WiredArrayChangeType.ARRAY_CREATED, -1, -1, -1, 0, null, null, 0, 0);
    }

    public static WiredArrayChange field(
            int index, int fieldId, long oldValue, long newValue, int oldLength, int newLength) {
        return new WiredArrayChange(
                WiredArrayChangeType.FIELD_VALUE_CHANGED,
                index,
                -1,
                -1,
                fieldId,
                oldValue,
                newValue,
                oldLength,
                newLength);
    }

    public static WiredArrayChange structural(
            WiredArrayStructuralOperation operation, int firstIndex, int secondIndex, int oldLength, int newLength) {
        int index =
                switch (operation) {
                    case APPEND -> oldLength;
                    case REMOVE_FIRST -> 0;
                    case REMOVE_LAST -> oldLength - 1;
                    case MOVE -> secondIndex;
                    case CLEAR, SHUFFLE -> -1;
                    default -> firstIndex;
                };
        boolean pair =
                operation == WiredArrayStructuralOperation.MOVE || operation == WiredArrayStructuralOperation.SWAP;
        return new WiredArrayChange(
                WiredArrayChangeType.from(operation),
                index,
                pair ? firstIndex : -1,
                pair ? secondIndex : -1,
                0,
                null,
                null,
                oldLength,
                newLength);
    }
}
