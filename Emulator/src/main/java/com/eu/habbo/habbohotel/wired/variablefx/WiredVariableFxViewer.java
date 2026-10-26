package com.eu.habbo.habbohotel.wired.variablefx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What one player in the room has been sent: the statuses their client is showing, by
 * signature, and the configs it knows. The diff of what they should be seeing against this is
 * what goes out; nothing else does.
 */
public final class WiredVariableFxViewer {
    private final Map<WiredVariableFxStatus.Key, String> sent = new HashMap<>();
    private boolean synced;
    /** The holders the viewer had statuses for at the last flush. */
    private Set<String> readyHolders = Set.of();

    /** A batch to send: the statuses that changed (or are new) and the keys that went away. */
    public record Batch(
            boolean initializeAll, List<WiredVariableFxStatus> updates, List<WiredVariableFxStatus.Key> removed) {
        public boolean isEmpty() {
            return this.updates.isEmpty() && this.removed.isEmpty();
        }
    }

    public boolean isSynced() {
        return this.synced;
    }

    public int sentCount() {
        return this.sent.size();
    }

    /** {@link #diff(Map, Set)} with no config resent. */
    public Batch diff(Map<WiredVariableFxStatus.Key, WiredVariableFxStatus> wanted) {
        return this.diff(wanted, Set.of());
    }

    /**
     * Works out what differs from what the viewer was last sent and remembers the new state. As in Habbo (observed
     * on AIR): the first batch initializes everything; a status of a holder the viewer already had is a change, not
     * an initialize; a holder new to the viewer is initialized; and a box saved again (its config resent) sends every
     * status of it again, initialized.
     */
    public Batch diff(Map<WiredVariableFxStatus.Key, WiredVariableFxStatus> wanted, Set<Integer> resentConfigs) {
        List<WiredVariableFxStatus.Key> removed = new ArrayList<>();
        for (WiredVariableFxStatus.Key key : this.sent.keySet()) {
            if (!wanted.containsKey(key)) removed.add(key);
        }
        for (WiredVariableFxStatus.Key key : removed) {
            this.sent.remove(key);
        }

        boolean initializeAll = !this.synced || !resentConfigs.isEmpty();
        List<WiredVariableFxStatus> updates = new ArrayList<>();
        Set<String> holders = new HashSet<>();
        for (Map.Entry<WiredVariableFxStatus.Key, WiredVariableFxStatus> entry : wanted.entrySet()) {
            WiredVariableFxStatus.Key key = entry.getKey();
            String holder = holderOf(key);
            holders.add(holder);
            String signature = entry.getValue().signature();
            String previous = this.sent.get(key);
            boolean resent = resentConfigs.contains(key.configId());

            if (!resent && previous != null && previous.equals(signature)) continue;

            this.sent.put(key, signature);
            boolean initialize = !this.synced || resent || !this.readyHolders.contains(holder);
            updates.add(entry.getValue().withInitialize(initialize));
        }

        this.readyHolders = holders;
        this.synced = true;

        return new Batch(initializeAll, updates, removed);
    }

    private static String holderOf(WiredVariableFxStatus.Key key) {
        return (key.userEntity() ? "u" : "f") + key.entityId();
    }
}
