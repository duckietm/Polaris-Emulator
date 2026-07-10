package com.eu.habbo.messages.outgoing.friends;

import com.eu.habbo.habbohotel.messenger.history.MessengerStoredMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class MessengerMessageV2Composer extends MessageComposer {
    private final MessengerStoredMessage message;

    public MessengerMessageV2Composer(MessengerStoredMessage message) {
        this.message = message;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.MessengerMessageV2Composer);
        response.appendInt(Math.toIntExact(message.conversationId()));
        MessengerHistoryComposer.appendMessage(response, message);
        return response;
    }
}
