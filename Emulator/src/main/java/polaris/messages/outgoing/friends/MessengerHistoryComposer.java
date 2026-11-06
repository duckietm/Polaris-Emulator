package polaris.messages.outgoing.friends;

import polaris.hotel.messenger.history.MessengerHistoryPage;
import polaris.hotel.messenger.history.MessengerStoredMessage;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public final class MessengerHistoryComposer extends MessageComposer {
    private final long conversationId;
    private final MessengerHistoryPage page;

    public MessengerHistoryComposer(long conversationId, MessengerHistoryPage page) {
        this.conversationId = conversationId;
        this.page = page;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.MessengerHistoryComposer);
        response.appendInt(Math.toIntExact(conversationId));
        response.appendBoolean(page.hasMore());
        response.appendInt(page.messages().size());
        for (MessengerStoredMessage message : page.messages()) appendMessage(response, message);
        return response;
    }

    static void appendMessage(ServerMessage response, MessengerStoredMessage message) {
        response.appendInt(Math.toIntExact(message.id()));
        response.appendInt(message.senderId());
        response.appendInt(message.type());
        response.appendString(message.message());
        response.appendString(message.metadata());
        response.appendInt(Math.toIntExact(message.createdAt()));
    }
}
