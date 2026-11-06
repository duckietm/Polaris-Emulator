package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.interactions.wired.chest.WiredTradingManager;
import polaris.hotel.items.interactions.wired.chest.WiredTradingSession;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

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
        User user = this.client.getUser();
        if (user == null) return;

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null) return;

        boolean confirm = this.packet.readBoolean();

        WiredTradingManager manager = room.getWiredRuntime().getTradingManager();
        WiredTradingSession session = manager.getSession(user);
        if (session == null) return;

        if (confirm) {
            // settle() closes the negotiation whichever way it goes, so there is nothing to push
            // afterwards: the client gets a completed or a cancelled instead.
            manager.settle(user, System.currentTimeMillis());
            return;
        }

        session.accept(System.currentTimeMillis());
        manager.pushState(user, session);
    }
}
