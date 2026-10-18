package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTransactionFailComposer;

/**
 * The official transaction failure reasons (AIR {@code wired_transactions.notification.fail.<n>}) and
 * how they reach the player.
 *
 * <p>Outside a trade window a failure arrives as {@code WiredTransactionFail}; inside one it closes
 * the window as {@code WiredTradeCancelled} with the same reason.
 */
public final class ChestTransactionFailures {
    /** Nothing to refuse. */
    public static final int NONE = 0;

    /** The player walked away (also "nothing to refuse" for the pre-AIR helpers). */
    public static final int USER_CANCELLED = 0;

    public static final int INVALID = 1;
    public static final int TIMEOUT = 2;
    public static final int TRADE_CANCELLED = 3;
    public static final int ALREADY_TRADING = 4;
    public static final int MISCONFIGURED = 5;
    public static final int INSUFFICIENT_FUNDS = 6;
    public static final int FUNDS_GONE = 7;
    public static final int EMPTY = 10;
    public static final int CHEST_FULL = 11;

    /** The furni asked about is not a wired chest at all ("feature disabled"). */
    public static final int NOT_A_WIRED_CHEST = 12;

    public static final int CHEST_NOT_IN_ROOM = 13;

    /** No chest that can be used: none there, all of them locked, or no rights to use them. */
    public static final int NO_OR_LOCKED_CHESTS = 15;

    public static final int TOO_MANY_OFFERS = 17;
    public static final int EXCEEDS_CAPACITY = 19;
    public static final int INTERNAL_ERROR = 1000;
    public static final int DATABASE_ERROR = 1001;

    private ChestTransactionFailures() {}

    /**
     * Why a player's own withdrawal from {@code item} is refused, or {@link #NONE}.
     *
     * @param hasRights whether the player has rights in the room, which withdrawing requires
     */
    public static int withdrawRefusal(HabboItem item, Habbo habbo, boolean hasRights) {
        if (!(item instanceof InteractionWiredChest chest)) return NOT_A_WIRED_CHEST;
        boolean owner = habbo != null && habbo.getHabboInfo().getId() == chest.getUserId();
        if ((!hasRights && !owner) || chest.isLockedFor(habbo)) return NO_OR_LOCKED_CHESTS;
        return NONE;
    }

    /**
     * Why a player may not start a deposit into {@code item}, or {@link #NONE}. AIR: anyone when the
     * chest takes donations, else someone with rights while the lock does not stand in their way.
     */
    public static int depositRefusal(HabboItem item, Habbo habbo, boolean hasRights) {
        if (!(item instanceof InteractionWiredChest chest)) return NOT_A_WIRED_CHEST;
        if (chest.getContents().isAccessDonate()) return NONE;
        boolean owner = habbo != null && habbo.getHabboInfo().getId() == chest.getUserId();
        if ((!hasRights && !owner) || chest.isLockedFor(habbo)) return NO_OR_LOCKED_CHESTS;
        return NONE;
    }

    /**
     * The official reason for a {@link WiredTradingSession} failure id (those predate the official
     * numbering and stay as they are for plugins).
     */
    public static int fromSession(int sessionFailure) {
        return switch (sessionFailure) {
            case WiredTradingSession.FAILURE_SILENT -> USER_CANCELLED;
            case WiredTradingSession.FAILURE_TIMEOUT -> TIMEOUT;
            case WiredTradingSession.FAILURE_REQUIREMENTS -> INSUFFICIENT_FUNDS;
            case WiredTradingSession.FAILURE_LEFT_ROOM -> TRADE_CANCELLED;
            case WiredTradingSession.FAILURE_CONTRACT_GONE -> MISCONFIGURED;
            case WiredTradingSession.FAILURE_REWARD_UNAVAILABLE -> FUNDS_GONE;
            default -> sessionFailure;
        };
    }

    /** Tell the player the transaction failed and why. */
    public static void send(Habbo habbo, int reason) {
        if (habbo == null || habbo.getClient() == null || reason == NONE) return;

        habbo.getClient().sendResponse(new WiredTransactionFailComposer(reason));
    }
}
