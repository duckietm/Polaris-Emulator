package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTradingManager;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTradingSession;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Both halves of agreeing, told apart by a single flag. Reads {@code bool confirm}.
 *
 * <p>{@code false} is the first press: the player agrees and the countdown starts, but nothing has
 * happened yet. {@code true} is the second, after the countdown, and that one settles. Splitting them
 * is what gives a player the moment to notice an offer that changed under them.
 */
public class WiredTradeAcceptEvent extends MessageHandler {
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

        boolean confirm = this.packet.readBoolean();

        WiredTradingManager manager = room.getWiredRuntime().getTradingManager();
        WiredTradingSession session = manager.getSession(habbo);
        if (session == null) return;

        if (confirm) {
            // settle() closes the negotiation whichever way it goes, so there is nothing to push
            // afterwards: the client gets a completed or a cancelled instead.
            manager.settle(habbo, System.currentTimeMillis());
            return;
        }

        session.accept(System.currentTimeMillis());
        manager.pushState(habbo, session);
    }
}
