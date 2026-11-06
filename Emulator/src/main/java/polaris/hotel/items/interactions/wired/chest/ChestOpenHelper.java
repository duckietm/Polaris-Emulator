package polaris.hotel.items.interactions.wired.chest;

import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.items.ChestCoinsComposer;
import polaris.messages.outgoing.rooms.items.ChestOpenComposer;

/**
 * The AIR open flow (WiredChestController): using the furni makes the server answer
 * {@code ChestOpen(chestId)}; the client then asks for the contents with its own open request and
 * gets {@code ChestCoins} (coin chest) or {@code ChestFurniChunk} fragments (furni chest). The
 * window's settings come from the furni data, not from a packet.
 */
public final class ChestOpenHelper {
    private ChestOpenHelper() {}

    /** Whether this player may look inside: everyone when the chest says so, else rights holders. */
    public static boolean mayOpen(InteractionWiredChest chest, User user, Room room) {
        if (chest == null || user == null || room == null) return false;
        return chest.getContents().isAccessOpen()
                || user.getUserInfo().getId() == chest.getUserId()
                || room.hasRights(user);
    }

    /** The furni was used: invite the client to open the chest. */
    public static void open(GameClient client, InteractionWiredChest chest, Room room) {
        if (client == null || chest == null || room == null) return;
        if (!mayOpen(chest, client.getUser(), room)) return;

        client.sendResponse(new ChestOpenComposer(chest.getId()));
    }

    /** The client asked for the contents: send them, and open the lid for the room. */
    public static void sendContents(GameClient client, InteractionWiredChest chest, Room room) {
        if (client == null || chest == null || room == null) return;
        if (!mayOpen(chest, client.getUser(), room)) return;

        chest.openFor(client.getUser(), room);

        if (chest.chestKind() == ChestStorage.KIND_CURRENCY) {
            client.sendResponse(
                    new ChestCoinsComposer(chest.getId(), chest.getContents().getCoins(), false));
        } else {
            ChestFurniPackets.sendFullChunks(client, chest.getId(), chest.getContents());
        }
    }
}
