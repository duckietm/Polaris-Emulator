package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.messages.outgoing.rooms.items.ChestFurniChunkComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ChestFurniDeltaComposer;

import java.util.ArrayList;
import java.util.List;

/** Helpers for furni-chest v2 chunk/delta protocol (headers 9322/9323). */
public final class ChestFurniPackets {
    private ChestFurniPackets() {
    }

    public static void sendFullChunks(GameClient client, int chestId, ChestStorage storage) {
        if (client == null || storage == null) return;

        List<ChestFurniStoredItem> items = storage.furniItems();
        int total = Math.max(1, (int) Math.ceil(items.size() / (double) ChestFurniChunkComposer.CHUNK_SIZE));
        if (items.isEmpty()) {
            client.sendResponse(new ChestFurniChunkComposer(chestId, 1, 0, List.of()));
            return;
        }
        for (int fragment = 0; fragment < total; fragment++) {
            int from = fragment * ChestFurniChunkComposer.CHUNK_SIZE;
            int to = Math.min(from + ChestFurniChunkComposer.CHUNK_SIZE, items.size());
            client.sendResponse(new ChestFurniChunkComposer(chestId, total, fragment, items.subList(from, to)));
        }
    }

    public static void sendDelta(GameClient client, int chestId, List<Integer> removedIds, List<ChestFurniStoredItem> added) {
        if (client == null) return;
        client.sendResponse(new ChestFurniDeltaComposer(
                chestId,
                removedIds == null ? List.of() : removedIds,
                added == null ? List.of() : added));
    }
}
