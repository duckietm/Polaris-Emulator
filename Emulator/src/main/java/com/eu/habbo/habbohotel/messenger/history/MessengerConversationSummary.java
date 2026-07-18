package com.eu.habbo.habbohotel.messenger.history;

public record MessengerConversationSummary(
        long id,
        ConversationType type,
        int participantId,
        String name,
        long lastMessageId,
        int unreadCount,
        long updatedAt
) {
}
