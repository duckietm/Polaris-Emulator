package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.Habbo;
import gnu.trove.set.hash.THashSet;

/**
 * Manages trading operations within a room.
 */
public class RoomTradeManager {
    private final Room room;
    private final THashSet<RoomTrade> activeTrades;

    public RoomTradeManager(Room room) {
        this.room = room;
        this.activeTrades = new THashSet<>(0);
    }

    /**
     * Starts a trade between two users.
     */
    public void startTrade(Habbo userOne, Habbo userTwo) {
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
    public RoomTrade getActiveTradeForHabbo(Habbo user) {
        synchronized (this.activeTrades) {
            for (RoomTrade trade : this.activeTrades) {
                for (RoomTradeUser habbo : trade.getRoomTradeUsers()) {
                    if (habbo.getHabbo() == user) {
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
    public THashSet<RoomTrade> getActiveTrades() {
        return this.activeTrades;
    }

    private boolean hasActiveTrade(Habbo user) {
        for (RoomTrade trade : this.activeTrades) {
            for (RoomTradeUser habbo : trade.getRoomTradeUsers()) {
                if (habbo.getHabbo() == user) {
                    return true;
                }
            }
        }

        return false;
    }
}
