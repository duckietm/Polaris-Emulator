package polaris.hotel.rooms;

import java.util.HashSet;
import java.util.Set;
import polaris.hotel.users.User;

/**
 * Manages trading operations within a room.
 */
public class RoomTradeManager {
    private final Room room;
    private final Set<RoomTrade> activeTrades;

    public RoomTradeManager(Room room) {
        this.room = room;
        this.activeTrades = new HashSet<>(0);
    }

    /**
     * Starts a trade between two users.
     */
    public void startTrade(User userOne, User userTwo) {
        RoomTrade trade;
        synchronized (this.activeTrades) {
            if (this.hasActiveTrade(userOne) || this.hasActiveTrade(userTwo)) {
                return;
            }

            trade = new RoomTrade(userOne, userTwo, this.room);
            this.activeTrades.add(trade);
        }

        trade.start();
    }

    /**
     * Stops a trade.
     */
    public void stopTrade(RoomTrade trade) {
        synchronized (this.activeTrades) {
            this.activeTrades.remove(trade);
        }
    }

    /**
     * Gets the active trade for a user.
     */
    public RoomTrade getActiveTradeForUser(User user) {
        synchronized (this.activeTrades) {
            for (RoomTrade trade : this.activeTrades) {
                for (RoomTradeUser tradeUser : trade.getRoomTradeUsers()) {
                    if (tradeUser.getUser() == user) {
                        return trade;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Gets all active trades.
     */
    public Set<RoomTrade> getActiveTrades() {
        return this.activeTrades;
    }

    private boolean hasActiveTrade(User user) {
        for (RoomTrade trade : this.activeTrades) {
            for (RoomTradeUser tradeUser : trade.getRoomTradeUsers()) {
                if (tradeUser.getUser() == user) {
                    return true;
                }
            }
        }

        return false;
    }
}
