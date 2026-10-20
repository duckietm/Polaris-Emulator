package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTradingManager;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTradingSession;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import java.util.List;

/**
 * AIR's transaction variables, all read only: {@code @transaction.*} on a user while a wired transaction window is
 * open for them (Initiate Transaction, a contract or a chest deposit), and {@code @event.transaction_complete.*} in
 * the stack a completed transaction fired.
 */
public final class WiredTransactionVariables {
    static final String IN_TRADE = "@transaction.in_trade";
    static final String START_TIME = "@transaction.start_time";
    static final String CONTRACT_ID = "@transaction.contract_id";
    static final String STATE = "@transaction.state";
    static final String CAN_ACCEPT = "@transaction.can_accept";
    static final String CURRENT_MULTIPLIER = "@transaction.current_multiplier";

    static final String COMPLETE_MULTIPLIER = "@event.transaction_complete.multiplier";
    static final String COMPLETE_DEPOSIT_FURNI = "@event.transaction_complete.deposit.furni_count";
    static final String COMPLETE_DEPOSIT_COINS = "@event.transaction_complete.deposit.coins_count";
    static final String COMPLETE_WITHDRAWAL_FURNI = "@event.transaction_complete.withdrawal.furni_count";
    static final String COMPLETE_WITHDRAWAL_COINS = "@event.transaction_complete.withdrawal.coins_count";

    /** AIR's {@code @transaction.state}: 1 Adding Items, 2 Confirmed (accepted, counting down or settling). */
    static final int STATE_ADDING_ITEMS = 1;

    static final int STATE_CONFIRMED = 2;

    static final List<String> USER_KEYS =
            List.of(IN_TRADE, START_TIME, CONTRACT_ID, STATE, CAN_ACCEPT, CURRENT_MULTIPLIER);

    static final List<String> COMPLETE_KEYS = List.of(
            COMPLETE_MULTIPLIER,
            COMPLETE_DEPOSIT_FURNI,
            COMPLETE_DEPOSIT_COINS,
            COMPLETE_WITHDRAWAL_FURNI,
            COMPLETE_WITHDRAWAL_COINS);

    private WiredTransactionVariables() {}

    static boolean isUserKey(String key) {
        return USER_KEYS.contains(key);
    }

    /** Whether the user holds the variable: any of them while in a transaction, the flags only while they are set. */
    static boolean has(Room room, Habbo habbo, String key) {
        Integer value = read(room, habbo, key);
        if (value == null) return false;
        return switch (key) {
            case IN_TRADE, CAN_ACCEPT -> value > 0;
            default -> true;
        };
    }

    /** The value for a user in a transaction; null for a user who is in none (and for the flags, 0 when unset). */
    static Integer read(Room room, Habbo habbo, String key) {
        WiredTradingManager trading = trading(room);
        WiredTradingSession session = trading == null || habbo == null ? null : trading.getSession(habbo);
        if (session == null || session.isClosed()) return IN_TRADE.equals(key) || CAN_ACCEPT.equals(key) ? 0 : null;

        return switch (key) {
            case IN_TRADE -> 1;
            case START_TIME -> (int) (session.openedAtMillis() / 1000L);
            case CONTRACT_ID -> trading.contractId(habbo);
            case STATE ->
                session.getState() == WiredTradingSession.STATE_ADDING_ITEMS ? STATE_ADDING_ITEMS : STATE_CONFIRMED;
            case CAN_ACCEPT -> session.canAccept() ? 1 : 0;
            case CURRENT_MULTIPLIER -> session.timesToSettle();
            default -> null;
        };
    }

    /** {@code @event.transaction_complete.*}: what the completed transaction moved; absent on other events. */
    static Integer readComplete(WiredContext ctx, String key) {
        if (ctx == null || ctx.event() == null || ctx.event().getType() != WiredEvent.Type.TRANSACTION_COMPLETE) {
            return null;
        }
        WiredEvent.TransactionTotals totals = ctx.event().getTransactionTotals();
        if (totals == null) totals = new WiredEvent.TransactionTotals(1, 0, 0, 0, 0);
        return switch (key) {
            case COMPLETE_MULTIPLIER -> totals.multiplier();
            case COMPLETE_DEPOSIT_FURNI -> totals.depositFurni();
            case COMPLETE_DEPOSIT_COINS -> totals.depositCoins();
            case COMPLETE_WITHDRAWAL_FURNI -> totals.withdrawalFurni();
            case COMPLETE_WITHDRAWAL_COINS -> totals.withdrawalCoins();
            default -> null;
        };
    }

    /**
     * The totals of a completed transaction: it ran {@code multiplier} times, the player paid {@code paidFurni} furni and
     * {@code paidCoins} coins into the chests and got {@code given} out of them.
     */
    public static WiredEvent.TransactionTotals totals(
            int multiplier, int paidFurni, int paidCoins, List<InteractionWiredContract.Term> given) {
        long furni = 0;
        long coins = 0;
        if (given != null) {
            for (InteractionWiredContract.Term term : given) {
                if (term == null || term.amount <= 0) continue;
                if (term.isCurrency()) coins += term.amount;
                else furni += term.amount;
            }
        }
        return new WiredEvent.TransactionTotals(
                Math.max(1, multiplier),
                Math.max(0, paidFurni),
                Math.max(0, paidCoins),
                (int) Math.min(furni, Integer.MAX_VALUE),
                (int) Math.min(coins, Integer.MAX_VALUE));
    }

    private static WiredTradingManager trading(Room room) {
        return room == null || room.getWiredRuntime() == null
                ? null
                : room.getWiredRuntime().getTradingManager();
    }
}
