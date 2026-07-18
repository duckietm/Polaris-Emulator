package com.eu.habbo.messages.outgoing.friends;

import com.eu.habbo.habbohotel.messenger.history.MessengerStoredMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class MessengerMessageAckComposer extends MessageComposer {
    private final int confirmationId;
    private final MessengerStoredMessage message;

    public MessengerMessageAckComposer(int confirmationId, MessengerStoredMessage message) {
        this.confirmationId = confirmationId;
        this.message = message;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.MessengerMessageAckComposer);
        response.appendInt(confirmationId);
        response.appendInt(Math.toIntExact(message.conversationId()));
        response.appendInt(Math.toIntExact(message.id()));
        response.appendInt(Math.toIntExact(message.createdAt()));
        return response;
    }
}
