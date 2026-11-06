package polaris.messages.incoming.friends;

import polaris.hotel.messenger.history.MessengerHistoryServices;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.MessengerConversationsComposer;

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
        int userId = client.getUser().getUserInfo().getId();
        client.sendResponse(new MessengerConversationsComposer(
                MessengerHistoryServices.create().listConversations(userId)));
    }
}
