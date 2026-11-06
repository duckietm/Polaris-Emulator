package polaris.messages.incoming.friends;

import polaris.Emulator;
import polaris.hotel.messenger.history.ConversationType;
import polaris.hotel.messenger.history.MessengerConversationSummary;
import polaris.hotel.messenger.history.MessengerHistoryService;
import polaris.hotel.messenger.history.MessengerHistoryServices;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.ConsoleReadReceiptComposer;
import polaris.messages.outgoing.friends.MessengerReadCursorComposer;

/**
 * The console marks the open conversation as read by the other user's id. The stored history is keyed
 * by conversation, so the direct conversation with that peer is resolved first and then read up to its
 * last message, exactly like {@link MarkMessengerReadEvent} does with an explicit conversation id.
 * The peer is told twice: the read cursor the messenger window follows, and the console receipt that
 * marks their own messages as read.
 */
public final class MarkConsoleReadEvent extends MessageHandler {

    /** It resolves the conversation through the same heavy listing the console open does. */
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() {
        int peerId = packet.readInt();
        if (!FriendInputGuard.isPositiveId(peerId)) return;

        User self = client.getUser();
        if (self == null) return;
        int userId = self.getUserInfo().getId();

        MessengerHistoryService history = MessengerHistoryServices.create();
        MessengerConversationSummary conversation = history.listConversations(userId).stream()
                .filter(summary -> summary.type() == ConversationType.DIRECT && summary.participantId() == peerId)
                .findFirst()
                .orElse(null);
        if (conversation == null || conversation.lastMessageId() <= 0) return;

        if (!history.markRead(conversation.id(), userId, conversation.lastMessageId())) return;

        for (int memberId : history.listActiveMemberIds(conversation.id(), userId)) {
            User member = Emulator.getGameEnvironment().getUserManager().getUser(memberId);
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
