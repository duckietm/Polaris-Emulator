package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.interactions.wired.chest.WiredTradingSession;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

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
        User user = this.client.getUser();
        if (user == null) return;

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null) return;

        room.getWiredRuntime().getTradingManager().cancel(user, WiredTradingSession.FAILURE_SILENT);
    }
}
