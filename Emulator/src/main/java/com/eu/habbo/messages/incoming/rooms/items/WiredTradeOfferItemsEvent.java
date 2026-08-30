package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTradingManager;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTradingSession;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts items on the negotiation table, or takes them off. Reads
 * {@code bool remove, int count, [int itemId]*}.
 *
 * <p>The count is bounded before anything is read, so a crafted packet cannot make the handler
 * allocate for a list that is not there.
 */
public class WiredTradeOfferItemsEvent extends MessageHandler {
    private static final int MAX_BATCH = WiredTradingSession.MAX_OFFERED_ITEMS;

    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        boolean remove = this.packet.readBoolean();
        int count = Math.min(Math.max(0, this.packet.readInt()), MAX_BATCH);

        List<Integer> itemIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) itemIds.add(this.packet.readInt());
        if (itemIds.isEmpty()) return;

        WiredTradingManager manager = room.getWiredRuntime().getTradingManager();
        WiredTradingSession session = manager.getSession(habbo);
        if (session == null) return;

        int changed = remove ? session.withdrawItems(itemIds) : session.offerItems(itemIds);
        if (changed > 0) this.client.sendResponse(new InventoryRefreshComposer());

        // Pushed even when nothing changed: the client asked, and a refused offer has to leave the
        // window showing what the server actually holds rather than what the drag suggested.
        manager.pushState(habbo, session);
    }
}
