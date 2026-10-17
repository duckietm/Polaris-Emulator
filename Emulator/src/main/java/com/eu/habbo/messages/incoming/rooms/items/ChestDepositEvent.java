package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * The old wallet deposit into a currency chest.
 *
 * <p>Retired with the AIR chest model: the official client has no such message, and everything goes
 * into a chest through the wired trade ({@link ChestStartDepositEvent}). No longer registered; kept
 * for plugins compiled against it, and it changes nothing if one registers it anyway.
 */
@Deprecated
public class ChestDepositEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        // Intentionally nothing.
    }
}
