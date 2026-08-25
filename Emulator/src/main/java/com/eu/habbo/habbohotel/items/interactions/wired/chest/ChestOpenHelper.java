package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ChestOpenComposer;

/**
 * Shared open flow for wired chests (official Nod → Syhytarer → shell + chunks).
 */
public final class ChestOpenHelper {
    private ChestOpenHelper() {
    }

    public static void open(GameClient client, InteractionWiredChest chest, Room room) {
        if (client == null || chest == null || room == null) return;
        if (!chest.getContents().isAccessOpen() && !room.hasRights(client.getHabbo())) return;

        client.sendResponse(new ChestOpenComposer(chest.getId()));
        client.sendResponse(new ChestDataComposer(chest));

        if (chest instanceof InteractionWiredChestFurni) {
            ChestFurniPackets.sendFullChunks(client, chest.getId(), chest.getContents());
        }
    }
}
