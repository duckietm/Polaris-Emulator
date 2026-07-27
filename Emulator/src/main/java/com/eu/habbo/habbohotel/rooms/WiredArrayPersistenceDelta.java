package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.wired.arrays.WiredArrayEntry;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayValue;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

record WiredArrayPersistenceDelta(
        int logicalLength, Set<Integer> removedIndexes, Map<Integer, WiredArrayEntry> upsertedEntries) {
    WiredArrayPersistenceDelta {
        removedIndexes = Collections.unmodifiableSet(new HashSet<>(removedIndexes));
        upsertedEntries = Collections.unmodifiableMap(new HashMap<>(upsertedEntries));
    }

    static WiredArrayPersistenceDelta between(WiredArrayValue previous, WiredArrayValue replacement) {
        Map<Integer, WiredArrayEntry> before = previous == null ? Collections.emptyMap() : previous.entriesView();
        Map<Integer, WiredArrayEntry> after = replacement.entriesView();
        Set<Integer> removed = new HashSet<>(before.keySet());
        removed.removeAll(after.keySet());
        Map<Integer, WiredArrayEntry> changed = new HashMap<>();
        for (Map.Entry<Integer, WiredArrayEntry> entry : after.entrySet()) {
            if (!entry.getValue().equals(before.get(entry.getKey()))) {
                changed.put(entry.getKey(), entry.getValue());
            }
        }
        return new WiredArrayPersistenceDelta(replacement.getLogicalLength(), removed, changed);
    }
}
