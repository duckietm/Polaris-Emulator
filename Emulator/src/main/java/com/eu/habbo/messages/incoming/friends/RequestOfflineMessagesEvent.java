package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.messages.incoming.MessageHandler;

public class RequestOfflineMessagesEvent extends MessageHandler {
    @Override
    public void handle() {
        this.client.getHabbo().getMessenger().deliverOfflineMessages(this.client.getHabbo());
    }
}
