package com.eu.habbo.messages.outgoing.friends;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class MessengerMessageFailedComposer extends MessageComposer {
    private final int confirmationId;
    private final int errorCode;

    public MessengerMessageFailedComposer(int confirmationId, int errorCode) {
        this.confirmationId = confirmationId;
        this.errorCode = errorCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.MessengerMessageFailedComposer);
        response.appendInt(confirmationId);
        response.appendInt(errorCode);
        return response;
    }
}
