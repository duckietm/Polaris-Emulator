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
}
