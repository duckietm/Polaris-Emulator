package com.eu.habbo.habbohotel.messenger.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MessengerHistoryService {
    public static final int DEFAULT_RETENTION_DAYS = 30;
    public static final int DEFAULT_MAX_MESSAGES = 500;
    public static final int DEFAULT_PAGE_SIZE = 30;
    public static final int MAX_PAGE_SIZE = 50;

    private final MessengerHistoryRepository repository;
    private final int retentionDays;
    private final int maxMessagesPerConversation;

    public MessengerHistoryService(MessengerHistoryRepository repository) {
        this(repository, DEFAULT_RETENTION_DAYS, DEFAULT_MAX_MESSAGES);
    }

    public MessengerHistoryService(MessengerHistoryRepository repository, int retentionDays, int maxMessagesPerConversation) {
        if (repository == null) throw new IllegalArgumentException("repository is required");
        if (retentionDays <= 0) throw new IllegalArgumentException("retentionDays must be positive");
        if (maxMessagesPerConversation <= 0) throw new IllegalArgumentException("maxMessagesPerConversation must be positive");
        this.repository = repository;
        this.retentionDays = retentionDays;
        this.maxMessagesPerConversation = maxMessagesPerConversation;
    }

    public MessengerHistoryPage loadHistory(long conversationId, int userId, long beforeMessageId, int requestedLimit) {
        if (conversationId <= 0 || userId <= 0) throw new IllegalArgumentException("conversationId and userId must be positive");
        if (!repository.isActiveMember(conversationId, userId)) throw new SecurityException("conversation access denied");

        int limit = Math.max(1, Math.min(MAX_PAGE_SIZE, requestedLimit));
        List<MessengerStoredMessage> loaded = repository.loadHistory(conversationId, userId, Math.max(0, beforeMessageId), limit);
        boolean hasMore = loaded.size() > limit;
        List<MessengerStoredMessage> page = new ArrayList<>(hasMore ? loaded.subList(0, limit) : loaded);
        Collections.reverse(page);
        return new MessengerHistoryPage(page, hasMore);
    }

    public List<MessengerConversationSummary> listConversations(int userId) {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        return List.copyOf(repository.listConversations(userId));
    }

    public MessengerStoredMessage sendMessage(long conversationId, int senderId, int recipientId, int type, String message, String metadata) {
        if (senderId <= 0) throw new IllegalArgumentException("senderId must be positive");
        if (type < 0 || type > 3) throw new IllegalArgumentException("unsupported message type");
        String normalized = message == null ? "" : message.strip();
        if (normalized.isEmpty() || normalized.length() > 255) throw new IllegalArgumentException("message length must be 1..255");
        if (metadata != null && metadata.length() > 1024) throw new IllegalArgumentException("metadata is too long");
        if (conversationId > 0) {
            if (!repository.isActiveMember(conversationId, senderId)) throw new SecurityException("conversation access denied");
            return repository.storeConversationMessage(conversationId, senderId, type, normalized, metadata);
        }
        if (recipientId <= 0 || recipientId == senderId) throw new IllegalArgumentException("invalid direct recipient");
        return repository.storeDirectMessage(senderId, recipientId, type, normalized, metadata);
    }

    public boolean markRead(long conversationId, int userId, long messageId) {
        if (conversationId <= 0 || userId <= 0 || messageId <= 0) throw new IllegalArgumentException("invalid read cursor");
        if (!repository.isActiveMember(conversationId, userId)) throw new SecurityException("conversation access denied");
        return repository.markRead(conversationId, userId, messageId);
    }

    public void cleanupRetention() {
        repository.cleanupRetention(retentionDays, maxMessagesPerConversation);
    }

    public static String directKey(int firstUserId, int secondUserId) {
        if (firstUserId <= 0 || secondUserId <= 0 || firstUserId == secondUserId) {
            throw new IllegalArgumentException("direct conversation requires two distinct positive user ids");
        }
        return Math.min(firstUserId, secondUserId) + ":" + Math.max(firstUserId, secondUserId);
    }
}
