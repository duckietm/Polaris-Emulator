package com.eu.habbo.habbohotel.wired.arrays;

import java.util.Map;

/** Read-only view of a published copy-on-write array value. */
public interface WiredArrayView {
    WiredArrayDefinition getDefinition();

    int getLogicalLength();

    int getOccupiedCount();

    int getLengthForCondition();

    int getAvailableIndexes();

    boolean isEmpty();

    boolean isFull();

    WiredArrayEntry getEntry(int index);

    Long readField(int index, int fieldId);

    Map<Integer, WiredArrayEntry> entriesView();

    default int findEntryIndex(long runtimeId) {
        if (runtimeId <= 0) return -1;
        for (Map.Entry<Integer, WiredArrayEntry> entry : entriesView().entrySet()) {
            if (entry.getValue().getRuntimeId() == runtimeId) return entry.getKey();
        }
        return -1;
    }
}
