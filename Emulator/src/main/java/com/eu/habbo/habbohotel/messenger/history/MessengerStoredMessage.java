package com.eu.habbo.habbohotel.messenger.history;

public record MessengerStoredMessage(
        long id,
        long conversationId,
        int senderId,
        int type,
        String message,
        String metadata,
        long createdAt
) {
}
