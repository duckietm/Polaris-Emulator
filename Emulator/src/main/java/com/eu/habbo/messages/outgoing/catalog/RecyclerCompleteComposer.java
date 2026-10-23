package com.eu.habbo.messages.outgoing.catalog;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class RecyclerCompleteComposer extends MessageComposer {
    public static final int RECYCLING_COMPLETE = 1;
    public static final int RECYCLING_CLOSED = 2;
    /** AIR RecyclerFinishedMessageEvent.FINISHED_FAIL: the recycle did not happen, the items stay in the inventory. */
    public static final int RECYCLING_FAILED = 2;

    private final int code;

    public RecyclerCompleteComposer(int code) {
        this.code = code;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RecyclerCompleteComposer);
        this.response.appendInt(this.code);
        this.response.appendInt(0); // prize ID.
        return this.response;
    }

    public int getCode() {
        return code;
    }
}
