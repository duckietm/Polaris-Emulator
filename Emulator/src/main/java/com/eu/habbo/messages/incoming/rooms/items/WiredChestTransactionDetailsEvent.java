package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestTransactionLog;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.WiredChestTransactionDetailsComposer;

/**
 * Requests the furni breakdown of one logged chest transaction. Reads {@code int transactionId} and
 * answers with {@link WiredChestTransactionDetailsComposer}.
 *
 * <p>The lookup is scoped to the room the player is standing in, so a guessed id cannot read another
 * room's log. An id that does not resolve is answered with silence rather than an error: the client
 * simply keeps the row collapsed.
 */
public class WiredChestTransactionDetailsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null || !room.hasRights(habbo)) return;

        int transactionId = this.packet.readInt();
        if (transactionId <= 0) return;

        ChestTransactionLog.Details details = ChestTransactionLog.details(room.getId(), transactionId);
        if (details == null) return;

        this.client.sendResponse(new WiredChestTransactionDetailsComposer(details));
    }
}
