package com.eu.habbo.habbohotel.messenger.history;

import java.util.List;

public interface MessengerHistoryRepository {
    List<MessengerConversationSummary> listConversations(int userId);

    boolean isActiveMember(long conversationId, int userId);

    List<Integer> listActiveMemberIds(long conversationId);

    List<MessengerStoredMessage> loadHistory(long conversationId, int userId, long beforeMessageId, int limit);

    MessengerStoredMessage storeDirectMessage(int senderId, int recipientId, int type, String message, String metadata);

    MessengerStoredMessage storeConversationMessage(long conversationId, int senderId, int type, String message, String metadata);

    boolean markRead(long conversationId, int userId, long messageId);

    void cleanupRetention(int days, int maxMessagesPerConversation);
}
