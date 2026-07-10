package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.habbohotel.messenger.history.MessengerHistoryServices;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.MessengerConversationsComposer;

public final class RequestMessengerConversationsEvent extends MessageHandler {
    @Override
    public void handle() {
        int userId = client.getHabbo().getHabboInfo().getId();
        client.sendResponse(new MessengerConversationsComposer(MessengerHistoryServices.create().listConversations(userId)));
    }
}
