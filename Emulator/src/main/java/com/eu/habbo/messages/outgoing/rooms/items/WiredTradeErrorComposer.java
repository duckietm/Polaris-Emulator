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
