package polaris.messages.incoming.friends;

import polaris.Emulator;
import polaris.hotel.messenger.history.MessengerHistoryService;
import polaris.hotel.messenger.history.MessengerHistoryServices;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.MessengerReadCursorComposer;

public final class MarkMessengerReadEvent extends MessageHandler {
    @Override
    public void handle() {
        int conversationId = packet.readInt();
        int messageId = packet.readInt();
        int userId = client.getUser().getUserInfo().getId();
        if (!FriendInputGuard.arePositiveIds(conversationId, messageId)) return;
        MessengerHistoryService history = MessengerHistoryServices.create();
        if (history.markRead(conversationId, userId, messageId)) {
            for (int memberId : history.listActiveMemberIds(conversationId, userId)) {
                User member = Emulator.getGameEnvironment().getUserManager().getUser(memberId);
                if (member != null && member.getClient() != null)
                    member.getClient().sendResponse(new MessengerReadCursorComposer(conversationId, userId, messageId));
            }
        }
    }
}
