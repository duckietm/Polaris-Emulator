package com.eu.habbo.messages.outgoing.friends;

import com.eu.habbo.habbohotel.messenger.history.MessengerConversationSummary;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

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
            response.appendString(conversation.name());
            response.appendInt(Math.toIntExact(conversation.lastMessageId()));
            response.appendInt(conversation.unreadCount());
            response.appendInt(Math.toIntExact(conversation.updatedAt()));
        }
        return response;
    }
}
