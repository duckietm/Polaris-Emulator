package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.history.MessengerHistoryService;
import com.eu.habbo.habbohotel.messenger.history.MessengerHistoryServices;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.MessengerReadCursorComposer;

public final class MarkMessengerReadEvent extends MessageHandler {
    @Override
    public void handle() {
        int conversationId = packet.readInt();
        int messageId = packet.readInt();
        int userId = client.getHabbo().getHabboInfo().getId();
        if (!FriendInputGuard.arePositiveIds(conversationId, messageId)) return;
        MessengerHistoryService history = MessengerHistoryServices.create();
        if (history.markRead(conversationId, userId, messageId)) {
            for (int memberId : history.listActiveMemberIds(conversationId, userId)) {
                Habbo member = Emulator.getGameEnvironment().getHabboManager().getHabbo(memberId);
                if (member != null && member.getClient() != null) member.getClient().sendResponse(new MessengerReadCursorComposer(conversationId, userId, messageId));
            }
        }
    }
}
