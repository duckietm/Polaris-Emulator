package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTradeCancelledComposer;

/**
 * The transaction failure reasons a chest answers with, and how they reach the player.
 *
 * <p>The client turns a reason into {@code wired_transactions.notification.fail.<reason>}, the same
 * notification a failed wired trade shows, so a refused chest action is said out loud instead of
 * answered with nothing.
 */
public final class ChestTransactionFailures {
    /** Nothing to refuse. */
    public static final int NONE = 0;

    /** The furni asked about is not a wired chest at all ("feature disabled"). */
    public static final int NOT_A_WIRED_CHEST = 12;

    /** No chest that can be used: none there, all of them locked, or no rights to use them. */
    public static final int NO_OR_LOCKED_CHESTS = 15;

    private ChestTransactionFailures() {}

    /**
     * Why a player's own withdrawal from {@code item} is refused, or {@link #NONE}.
     *
     * @param hasRights whether the player has rights in the room, which withdrawing requires
     */
    public static int withdrawRefusal(HabboItem item, Habbo habbo, boolean hasRights) {
        if (!(item instanceof InteractionWiredChest chest)) return NOT_A_WIRED_CHEST;
        if (!hasRights || chest.isLockedFor(habbo)) return NO_OR_LOCKED_CHESTS;
        return NONE;
    }

    /** Tell the player the transaction failed and why. */
    public static void send(Habbo habbo, int reason) {
        if (habbo == null || habbo.getClient() == null || reason == NONE) return;

        habbo.getClient().sendResponse(new WiredTradeCancelledComposer(reason));
    }
}
