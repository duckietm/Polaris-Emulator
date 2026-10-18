package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.items.ChestCoinsComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ChestOpenComposer;

/**
 * The AIR open flow (WiredChestController): using the furni makes the server answer
 * {@code ChestOpen(chestId)}; the client then asks for the contents with its own open request and
 * gets {@code ChestCoins} (coin chest) or {@code ChestFurniChunk} fragments (furni chest). The
 * window's settings come from the furni data, not from a packet.
 */
public final class ChestOpenHelper {
    private ChestOpenHelper() {}

    /** Whether this player may look inside: everyone when the chest says so, else rights holders. */
    public static boolean mayOpen(InteractionWiredChest chest, Habbo habbo, Room room) {
        if (chest == null || habbo == null || room == null) return false;
        return chest.getContents().isAccessOpen()
                || habbo.getHabboInfo().getId() == chest.getUserId()
                || room.hasRights(habbo);
    }

    /** The furni was used: invite the client to open the chest. */
    public static void open(GameClient client, InteractionWiredChest chest, Room room) {
        if (client == null || chest == null || room == null) return;
        if (!mayOpen(chest, client.getHabbo(), room)) return;

        client.sendResponse(new ChestOpenComposer(chest.getId()));
    }

    /** The client asked for the contents: send them, and open the lid for the room. */
    public static void sendContents(GameClient client, InteractionWiredChest chest, Room room) {
        if (client == null || chest == null || room == null) return;
        if (!mayOpen(chest, client.getHabbo(), room)) return;

        chest.openFor(client.getHabbo(), room);

        if (chest.chestKind() == ChestStorage.KIND_CURRENCY) {
            client.sendResponse(
                    new ChestCoinsComposer(chest.getId(), chest.getContents().getCoins(), false));
        } else {
            ChestFurniPackets.sendFullChunks(client, chest.getId(), chest.getContents());
        }
    }
}
