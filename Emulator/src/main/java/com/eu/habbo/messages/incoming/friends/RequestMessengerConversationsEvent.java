package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.habbohotel.messenger.history.MessengerHistoryServices;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.MessengerConversationsComposer;

public final class RequestMessengerConversationsEvent extends MessageHandler {

    /**
     * Listing the conversations is the heaviest read the messenger does - several
     * aggregates over every message of every conversation the user is in - and the
     * client only needs it when the console is opened or refreshed.
     */
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() {
        int userId = client.getHabbo().getHabboInfo().getId();
        client.sendResponse(new MessengerConversationsComposer(
                MessengerHistoryServices.create().listConversations(userId)));
    }
}
