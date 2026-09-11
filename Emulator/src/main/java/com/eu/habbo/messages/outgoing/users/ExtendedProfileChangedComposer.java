package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * This profile is not what it was a moment ago. A client showing it asks for it again; one showing
 * somebody else's ignores the packet.
 */
public class ExtendedProfileChangedComposer extends MessageComposer {
    private final int userId;

    public ExtendedProfileChangedComposer(int userId) {
        this.userId = userId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ExtendedProfileChangedComposer);
        this.response.appendInt(this.userId);
        return this.response;
    }

    public int getUserId() {
        return userId;
    }
}
