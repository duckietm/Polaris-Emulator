package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomWiredVariableCatalog;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredAllVariablesDiffComposer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Official AIR 13 {@code WiredVariablesSynchronizer} diff request (1497): the client sends the hash
 * of every wired variable it has cached, and gets back what to drop and what to add or replace.
 */
public class WiredVariableHashesEvent extends MessageHandler {
    private static final int MAX_CACHED_ENTRIES = 4096;
    private static final int CHUNK_SIZE = 100;

    @Override
    public void handle() throws Exception {
        Map<String, Integer> cachedHashes = new HashMap<>();
        int cachedCount = this.packet.readInt();

        for (int index = 0; index < cachedCount && index < MAX_CACHED_ENTRIES; index++) {
            String variableId = this.packet.readString();
            int hash = this.packet.readInt();
            cachedHashes.put(variableId, hash);
        }

        Room room = currentRoom();

        if (room == null || !room.canInspectWired(this.client.getHabbo())) {
            return;
        }

        List<RoomWiredVariableCatalog.Variable> variables = RoomWiredVariableCatalog.variables(room);
        int allVariablesHash = RoomWiredVariableCatalog.allVariablesHash(variables);
        Map<String, RoomWiredVariableCatalog.Variable> current = RoomWiredVariableCatalog.byId(variables);

        List<String> removed = new ArrayList<>();
        for (String cachedId : cachedHashes.keySet()) {
            if (!current.containsKey(cachedId)) {
                removed.add(cachedId);
            }
        }

        List<RoomWiredVariableCatalog.Variable> changed = new ArrayList<>();
        for (RoomWiredVariableCatalog.Variable variable : variables) {
            Integer cachedHash = cachedHashes.get(variable.getVariableId());

            if (cachedHash == null || cachedHash != variable.hash()) {
                changed.add(variable);
            }
        }

        this.sendDiff(allVariablesHash, removed, changed);
    }

    private void sendDiff(int allVariablesHash, List<String> removed, List<RoomWiredVariableCatalog.Variable> changed) {
        if (changed.isEmpty()) {
            this.client.sendResponse(new WiredAllVariablesDiffComposer(allVariablesHash, true, removed, List.of()));
            return;
        }

        // The removals ride along with the first chunk; only the last chunk closes the exchange.
        for (int offset = 0; offset < changed.size(); offset += CHUNK_SIZE) {
            int end = Math.min(offset + CHUNK_SIZE, changed.size());
            boolean lastChunk = end >= changed.size();

            this.client.sendResponse(new WiredAllVariablesDiffComposer(
                    allVariablesHash,
                    lastChunk,
                    (offset == 0) ? removed : List.of(),
                    new ArrayList<>(changed.subList(offset, end))));
        }
    }

    @Override
    public int getRatelimit() {
        return 50;
    }
}
