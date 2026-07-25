package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.WiredFurniMoveStyleComposer;
import java.util.Collection;

/**
 * Sends the capability-gated move-style hint (header 5110) ahead of wired movement packets.
 * Clients that never announced {@link GameClient#WIRED_FEATURE_MOVE_STYLE} receive nothing and
 * keep the historical linear animation.
 */
public final class WiredMoveStyleHelper {
    public static final int STYLE_LINEAR = 0;
    public static final int STYLE_DROP = 6;
    public static final int DROP_INTENSITY = 100;

    private WiredMoveStyleHelper() {}

    public static void broadcast(Room room, Collection<Integer> itemIds, int style, int intensity) {
        if (room == null || itemIds == null || itemIds.isEmpty() || style == STYLE_LINEAR) {
            return;
        }

        var message = new WiredFurniMoveStyleComposer(itemIds, style, intensity).compose();
        for (Habbo habbo : room.getHabbos()) {
            if (habbo == null || habbo.getClient() == null) {
                continue;
            }
            if (habbo.getClient()
                    .supportsWiredFeature(
                            GameClient.WIRED_FEATURE_PROTOCOL_VERSION, GameClient.WIRED_FEATURE_MOVE_STYLE)) {
                habbo.getClient().sendResponse(message);
            }
        }
    }
}
