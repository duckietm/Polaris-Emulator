package polaris.messages.incoming.friends;

import polaris.hotel.messenger.history.MessengerHistoryPage;
import polaris.hotel.messenger.history.MessengerHistoryServices;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.MessengerHistoryComposer;

public final class RequestMessengerHistoryEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() {
        int conversationId = packet.readInt();
        int beforeMessageId = packet.readInt();
        int limit = packet.readInt();
        if (!FriendInputGuard.isPositiveId(conversationId) || beforeMessageId < 0) return;
        int userId = client.getUser().getUserInfo().getId();
        MessengerHistoryPage page;
        try {
            page = MessengerHistoryServices.create().loadHistory(conversationId, userId, beforeMessageId, limit);
        } catch (SecurityException exception) {
            // Not a member of that conversation: nothing to send.
            return;
        }
        client.sendResponse(new MessengerHistoryComposer(conversationId, page));
    }
}
