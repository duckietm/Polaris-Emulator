package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
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

    /** A give-from-chest box set to give everything resolved more than one user (AIR "cannot give all to many"). */
    public static final int CANNOT_GIVE_ALL_TO_MANY = 16;

    public static final int TOO_MANY_OFFERS = 17;
    public static final int EXCEEDS_CAPACITY = 19;

    /**
     * Initiate Transaction resolved no contract or more than one (turbo "Misconfig Too Many Or No Contracts"): AIR
     * runs exactly one. A setup error, so it counts on the monitor.
     */
    public static final int MISCONFIG_TOO_MANY_OR_NO_CONTRACTS = 21;

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

    /**
     * Whether a failed wired transaction counts on the wired monitor (Habbo's TRANSACTION_FAILURE, error 7). The
     * monitor lists what the room's builder or the hotel has to look at, so it counts the failures behind which stand
     * the room's setup or stock (a misconfigured contract, no usable chest, a chest that ran out of the reward or is
     * full) and the server's own errors. What the player does is part of playing and is left to the Transaction Failed
     * trigger: walking away, letting it time out, a cancelled trade, an empty or invalid offer, too many offers, not
     * enough to pay.
     */
    public static boolean countsOnMonitor(int reason) {
        return switch (reason) {
            case USER_CANCELLED,
                    INVALID,
                    TIMEOUT,
                    TRADE_CANCELLED,
                    ALREADY_TRADING,
                    INSUFFICIENT_FUNDS,
                    EMPTY,
                    TOO_MANY_OFFERS -> false;
            default -> true;
        };
    }

    /**
     * Counts a failed wired transaction in the room's wired diagnostics when {@link #countsOnMonitor} says it is one
     * for the monitor.
     *
     * @param sourceLabel what ran the transaction, for the detail line
     * @param sourceId the furni behind it, or 0
     */
    public static void noteOnMonitor(Room room, int reason, String sourceLabel, int sourceId) {
        if (room == null || !countsOnMonitor(reason)) return;

        WiredManager.noteTransactionFailure(
                room.getId(), "Transaction failed: " + describe(reason), sourceLabel, sourceId);
    }

    /** A short name of a failure reason for the monitor's detail line. */
    static String describe(int reason) {
        return switch (reason) {
            case MISCONFIGURED -> "misconfigured (" + reason + ")";
            case FUNDS_GONE -> "the chests no longer hold the reward (" + reason + ")";
            case CHEST_FULL -> "the chests are full (" + reason + ")";
            case NOT_A_WIRED_CHEST -> "not a wired chest (" + reason + ")";
            case CHEST_NOT_IN_ROOM -> "the chest is not in the room (" + reason + ")";
            case NO_OR_LOCKED_CHESTS -> "no usable chest (" + reason + ")";
            case CANNOT_GIVE_ALL_TO_MANY -> "everything cannot go to several users (" + reason + ")";
            case EXCEEDS_CAPACITY -> "over the chest capacity (" + reason + ")";
            case MISCONFIG_TOO_MANY_OR_NO_CONTRACTS -> "not exactly one contract (" + reason + ")";
            case INTERNAL_ERROR -> "internal error (" + reason + ")";
            case DATABASE_ERROR -> "database error (" + reason + ")";
            default -> "reason " + reason;
        };
    }

    /** Tell the player the transaction failed and why. */
    public static void send(Habbo habbo, int reason) {
        if (habbo == null || habbo.getClient() == null || reason == NONE) return;

        habbo.getClient().sendResponse(new WiredTransactionFailComposer(reason));
    }
}
