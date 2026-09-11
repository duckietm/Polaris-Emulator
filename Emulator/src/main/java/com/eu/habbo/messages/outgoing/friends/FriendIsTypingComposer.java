package com.eu.habbo.messages.outgoing.friends;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class FriendIsTypingComposer extends MessageComposer {
    private final int senderId;
    private final boolean typing;

    public FriendIsTypingComposer(int senderId, boolean typing) {
        this.senderId = senderId;
        this.typing = typing;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.FriendIsTypingComposer);
        response.appendInt(senderId);
        response.appendBoolean(typing);
        return response;
    }
}
