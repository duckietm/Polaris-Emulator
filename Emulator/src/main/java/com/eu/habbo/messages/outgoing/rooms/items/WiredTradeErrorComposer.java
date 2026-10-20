package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * An offer in an open wired trade was refused (AIR {@code WiredTradeError}): {@code int errorId},
 * shown as {@code wired_transactions.notification.trade_error.<id>}.
 */
public class WiredTradeErrorComposer extends MessageComposer {
    /** Not an item that can be offered here. */
    public static final int INVALID_ITEM = 0;

    /** More items than a trade takes. */
    public static final int TOO_MANY_ITEMS = 1;

    /** More kinds of furni than the table has slots for. */
    public static final int TOO_MANY_DIFFERENT_ITEMS = 2;

    /** The table already meets every requirement ({@code trade_error.4}). */
    public static final int ALREADY_MET = 4;

    /** More than the contract asks: a furni it does not ask for, more of one, or more coins ({@code trade_error.5}). */
    public static final int EXCEEDS_REQUIREMENTS = 5;

    /** More than the chests can hold. */
    public static final int EXCEEDS_CAPACITY = 9;

    private final int errorId;

    public WiredTradeErrorComposer(int errorId) {
        this.errorId = errorId;
    }

    public int getErrorId() {
        return this.errorId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredTradeErrorComposer);
        this.response.appendInt(this.errorId);
        return this.response;
    }
}
