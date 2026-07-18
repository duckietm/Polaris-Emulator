package com.eu.habbo.messages.outgoing.friends;

import com.eu.habbo.habbohotel.messenger.history.MessengerStoredMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class MessengerMessageComposer extends MessageComposer {
    private final MessengerStoredMessage message;

    public MessengerMessageComposer(MessengerStoredMessage message) {
        this.message = message;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.MessengerMessageComposer);
        response.appendInt(Math.toIntExact(message.conversationId()));
        MessengerHistoryComposer.appendMessage(response, message);
        return response;
    }
}
