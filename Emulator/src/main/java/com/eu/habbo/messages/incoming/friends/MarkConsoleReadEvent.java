package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.history.ConversationType;
import com.eu.habbo.habbohotel.messenger.history.MessengerConversationSummary;
import com.eu.habbo.habbohotel.messenger.history.MessengerHistoryService;
import com.eu.habbo.habbohotel.messenger.history.MessengerHistoryServices;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.ConsoleReadReceiptComposer;
import com.eu.habbo.messages.outgoing.friends.MessengerReadCursorComposer;

/**
 * The console marks the open conversation as read by the other user's id. The stored history is keyed
 * by conversation, so the direct conversation with that peer is resolved first and then read up to its
 * last message, exactly like {@link MarkMessengerReadEvent} does with an explicit conversation id.
 * The peer is told twice: the read cursor the messenger window follows, and the console receipt that
 * marks their own messages as read.
 */
public final class MarkConsoleReadEvent extends MessageHandler {
    @Override
    public void handle() {
        int peerId = packet.readInt();
        if (!FriendInputGuard.isPositiveId(peerId)) return;

        Habbo self = client.getHabbo();
        if (self == null) return;
        int userId = self.getHabboInfo().getId();

        MessengerHistoryService history = MessengerHistoryServices.create();
        MessengerConversationSummary conversation = history.listConversations(userId).stream()
                .filter(summary -> summary.type() == ConversationType.DIRECT && summary.participantId() == peerId)
                .findFirst()
                .orElse(null);
        if (conversation == null || conversation.lastMessageId() <= 0) return;

        if (!history.markRead(conversation.id(), userId, conversation.lastMessageId())) return;

        for (int memberId : history.listActiveMemberIds(conversation.id(), userId)) {
            Habbo member = Emulator.getGameEnvironment().getHabboManager().getHabbo(memberId);
            if (member != null && member.getClient() != null) {
                member.getClient()
                        .sendResponse(new MessengerReadCursorComposer(
                                conversation.id(), userId, conversation.lastMessageId()));
                if (memberId == peerId) {
                    member.getClient().sendResponse(new ConsoleReadReceiptComposer(userId));
                }
            }
        }
    }
}
