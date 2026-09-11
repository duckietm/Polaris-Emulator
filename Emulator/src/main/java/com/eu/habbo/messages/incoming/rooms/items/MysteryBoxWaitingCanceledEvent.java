package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.mysterybox.MysteryBoxManager;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * The sender walked away from a mystery box wait. The packet names the owner of the box, which we do
 * not need: somebody is in at most one wait, and that is the one they are leaving. The other side is
 * told the wait is over.
 */
public class MysteryBoxWaitingCanceledEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        this.packet.readInt();

        MysteryBoxManager.getInstance().cancel(this.client.getHabbo());
    }
}
