package polaris.messages.outgoing.friends;

import java.util.List;
import polaris.hotel.messenger.history.MessengerConversationSummary;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public final class MessengerConversationsComposer extends MessageComposer {
    private final List<MessengerConversationSummary> conversations;

    public MessengerConversationsComposer(List<MessengerConversationSummary> conversations) {
        this.conversations = List.copyOf(conversations);
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.MessengerConversationsComposer);
        response.appendInt(conversations.size());
        for (MessengerConversationSummary conversation : conversations) {
            response.appendInt(Math.toIntExact(conversation.id()));
            response.appendInt(conversation.type().ordinal());
            response.appendInt(conversation.participantId());
            response.appendString(conversation.name());
            response.appendInt(Math.toIntExact(conversation.lastMessageId()));
            response.appendInt(conversation.unreadCount());
            response.appendInt(Math.toIntExact(conversation.updatedAt()));
        }
        return response;
    }
}
