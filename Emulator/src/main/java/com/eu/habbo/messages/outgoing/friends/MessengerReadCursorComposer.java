package com.eu.habbo.messages.outgoing.friends;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class MessengerReadCursorComposer extends MessageComposer {
    private final long conversationId;
    private final int readerId;
    private final long messageId;

    public MessengerReadCursorComposer(long conversationId, int readerId, long messageId) {
        this.conversationId = conversationId;
        this.readerId = readerId;
        this.messageId = messageId;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.MessengerReadCursorComposer);
        response.appendInt(Math.toIntExact(conversationId));
        response.appendInt(readerId);
        response.appendInt(Math.toIntExact(messageId));
        return response;
    }
}
