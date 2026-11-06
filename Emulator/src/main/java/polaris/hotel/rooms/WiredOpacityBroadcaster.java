package polaris.hotel.rooms;

import java.util.Collection;
import java.util.List;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.WiredFurniOpacityComposer;

/**
 * Sends furniture opacity updates only to clients that announced opacity support, so clients that
 * never negotiated the capability keep the historical rendering.
 */
public final class WiredOpacityBroadcaster {

    private WiredOpacityBroadcaster() {}

    public static void broadcast(Room room, Collection<WiredOpacityState> updates, int easing, int durationMs) {
        if (room == null || updates == null || updates.isEmpty()) {
            return;
        }

        List<WiredOpacityState> payload = List.copyOf(updates);
        for (User user : room.getUsers()) {
            if (!supportsOpacity(user)) {
                continue;
            }
            user.getClient().sendResponse(new WiredFurniOpacityComposer(room.getId(), payload, easing, durationMs));
        }
    }

    public static boolean supportsOpacity(User user) {
        return user != null
                && user.getClient() != null
                && user.getClient()
                        .supportsWiredFeature(
                                GameClient.WIRED_FEATURE_PROTOCOL_VERSION, GameClient.WIRED_FEATURE_OPACITY);
    }
}
