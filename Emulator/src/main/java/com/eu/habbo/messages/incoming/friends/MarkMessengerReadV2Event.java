package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.habbohotel.messenger.history.MessengerHistoryServices;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.MessengerReadCursorComposer;

public final class MarkMessengerReadV2Event extends MessageHandler {
    @Override
    public void handle() {
        int conversationId = packet.readInt();
        int messageId = packet.readInt();
        int userId = client.getHabbo().getHabboInfo().getId();
        if (conversationId <= 0 || messageId <= 0) return;
        if (MessengerHistoryServices.create().markRead(conversationId, userId, messageId)) {
            client.sendResponse(new MessengerReadCursorComposer(conversationId, userId, messageId));
        }
    }
}
