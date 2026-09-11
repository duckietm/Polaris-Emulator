package com.eu.habbo.messages.outgoing.catalog;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * AIR 13 event 2316 (`HabboCatalog.onLtdRaffleResult`): the raffle for a limited-edition
 * item is over. The official client shows `notification.raffle.won` for result code 0 and
 * `notification.raffle.lost` for anything else.
 */
public class LtdRaffleResultComposer extends MessageComposer {
    /** The player got the item. */
    public static final int WON = 0;
    /** Somebody else got it. */
    public static final int LOST = 1;
    /** The raffle was cancelled before it could be drawn. */
    public static final int CANCELLED = 2;
    /** The raffle could not be resolved. */
    public static final int ERROR = 3;

    private final String className;
    private final int resultCode;

    public LtdRaffleResultComposer(String className, int resultCode) {
        this.className = className == null ? "" : className;
        this.resultCode = resultCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.LtdRaffleResultComposer);
        this.response.appendString(this.className);
        this.response.appendByte(this.resultCode);
        return this.response;
    }

    public String getClassName() {
        return this.className;
    }

    public int getResultCode() {
        return this.resultCode;
    }
}
