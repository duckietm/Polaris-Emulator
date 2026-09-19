package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

// The header alias is deprecated in Outgoing; the composer stays for plugin compatibility.
@SuppressWarnings("deprecation")
public class UnknownGuildComposer3 extends MessageComposer {
    private final int userId;

    public UnknownGuildComposer3(int userId) {
        this.userId = userId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UnknownGuildComposer3);
        this.response.appendInt(this.userId);
        return this.response;
    }

    public int getUserId() {
        return userId;
    }
}
