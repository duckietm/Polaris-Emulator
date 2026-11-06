package polaris.messages.incoming.rooms.items;

import java.util.ArrayList;
import java.util.List;
import polaris.hotel.items.interactions.wired.chest.WiredTradingManager;
import polaris.hotel.items.interactions.wired.chest.WiredTradingSession;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;

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
        User user = this.client.getUser();
        if (user == null) return;

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null) return;

        boolean remove = this.packet.readBoolean();
        int count = Math.min(Math.max(0, this.packet.readInt()), MAX_BATCH);

        List<Integer> itemIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            itemIds.add(BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt()));
        if (itemIds.isEmpty()) return;

        WiredTradingManager manager = room.getWiredRuntime().getTradingManager();
        WiredTradingSession session = manager.getSession(user);
        if (session == null) return;

        int changed = manager.offer(user, itemIds, remove);
        if (changed > 0) this.client.sendResponse(new InventoryRefreshComposer());

        // Pushed even when nothing changed: the client asked, and a refused offer has to leave the
        // window showing what the server actually holds rather than what the drag suggested.
        manager.pushState(user, session);
    }
}
