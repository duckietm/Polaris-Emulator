package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.WiredFurniOpacityComposer;
import java.util.Collection;
import java.util.List;

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
        for (Habbo habbo : room.getHabbos()) {
            if (!supportsOpacity(habbo)) {
                continue;
            }
            habbo.getClient().sendResponse(new WiredFurniOpacityComposer(room.getId(), payload, easing, durationMs));
        }
    }

    public static boolean supportsOpacity(Habbo habbo) {
        return habbo != null
                && habbo.getClient() != null
                && habbo.getClient()
                        .supportsWiredFeature(
                                GameClient.WIRED_FEATURE_PROTOCOL_VERSION, GameClient.WIRED_FEATURE_OPACITY);
    }
}
