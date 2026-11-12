package polaris.hotel.users.inventory;

import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserPrefix;
import polaris.messages.outgoing.inventory.nickicons.UserNickIconsComposer;
import polaris.messages.outgoing.inventory.prefixes.ActivePrefixUpdatedComposer;
import polaris.messages.outgoing.inventory.prefixes.PrefixReceivedComposer;
import polaris.messages.outgoing.inventory.prefixes.UserPrefixesComposer;
import polaris.messages.outgoing.rooms.users.RoomUserDataComposer;

/** The packets that follow a change to a user's prefixes, so the owner and the room see the same state. */
public final class PrefixUpdates {

    private PrefixUpdates() {}

    /** A new prefix that is now the active one: the owner gets it, and the room sees it next to the name. */
    public static void received(User user, UserPrefix prefix) {
        GameClient client = user.getClient();
        if (client != null) {
            client.sendResponse(new PrefixReceivedComposer(prefix));
            client.sendResponse(new ActivePrefixUpdatedComposer(prefix));
            client.sendResponse(new UserNickIconsComposer(user));
        }
        broadcast(user);
    }

    /** After one or more prefixes were removed; {@code activeRemoved} clears the one shown in the room too. */
    public static void removed(User user, boolean activeRemoved) {
        GameClient client = user.getClient();
        if (client != null) {
            if (activeRemoved) {
                client.sendResponse(new ActivePrefixUpdatedComposer(null));
            }
            client.sendResponse(new UserPrefixesComposer(user));
            client.sendResponse(new UserNickIconsComposer(user));
        }
        if (activeRemoved) {
            broadcast(user);
        }
    }

    private static void broadcast(User user) {
        Room room = user.getUserInfo().getCurrentRoom();
        if (room != null) {
            room.sendComposer(new RoomUserDataComposer(user).compose());
        }
    }
}
