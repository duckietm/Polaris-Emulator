package com.eu.habbo.habbohotel.games.snowwar;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Charges a game when a match is formed and keeps track of players who paid
 * for a match that has not started yet, so they are never charged twice and
 * get the game back when the start falls through.
 */
final class SnowWarGameCharges {

    private final SnowWarGameAllowance allowance;
    private final Supplier<LocalDate> today;
    private final IntSupplier freePerDay;
    private final Set<Integer> paidPending = ConcurrentHashMap.newKeySet();

    SnowWarGameCharges(SnowWarGameAllowance allowance, Supplier<LocalDate> today, IntSupplier freePerDay) {
        this.allowance = allowance;
        this.today = today;
        this.freePerDay = freePerDay;
    }

    /** Charges one game unless this player already paid for the match being formed. */
    SnowWarGameAllowance.ConsumeResult charge(int userId) {
        if (this.paidPending.contains(userId)) {
            return SnowWarGameAllowance.ConsumeResult.TOKEN;
        }
        SnowWarGameAllowance.ConsumeResult result =
                this.allowance.tryConsumeGame(userId, this.today.get(), this.freePerDay.getAsInt());
        if (result.charged()) {
            this.paidPending.add(userId);
        }
        return result;
    }

    /** The match started: the paid games are used. */
    void started(Collection<Integer> userIds) {
        this.paidPending.removeAll(userIds);
    }

    /** Gives the game back to a player who paid for a match that did not start. */
    boolean refundIfPaid(int userId) {
        if (!this.paidPending.remove(userId)) {
            return false;
        }
        return this.allowance.refundGame(userId);
    }

    /** Gives the games back for a match that failed to start. */
    void refund(Collection<Integer> userIds) {
        for (Integer userId : userIds) {
            this.allowance.refundGame(userId);
        }
    }

    boolean hasPaid(int userId) {
        return this.paidPending.contains(userId);
    }

    int gamesLeft(int userId) {
        return this.allowance.gamesLeft(userId, this.today.get(), this.freePerDay.getAsInt());
    }
}
