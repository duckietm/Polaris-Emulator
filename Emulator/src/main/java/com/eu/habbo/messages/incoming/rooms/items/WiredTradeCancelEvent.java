package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTradingSession;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * The player walks away from a negotiation. Carries nothing.
 *
 * <p>Cancelled with the silent reason: they did this on purpose and do not need to be told why.
 */
public class WiredTradeCancelEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        room.getWiredRuntime().getTradingManager().cancel(habbo, WiredTradingSession.FAILURE_SILENT);
    }
}
