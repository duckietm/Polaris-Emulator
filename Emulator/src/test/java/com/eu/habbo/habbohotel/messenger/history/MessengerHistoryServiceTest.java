package com.eu.habbo.habbohotel.messenger.history;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessengerHistoryServiceTest {
    @Test
    void directKeyIsStableRegardlessOfSenderOrder() {
        assertEquals("7:19", MessengerHistoryService.directKey(7, 19));
        assertEquals("7:19", MessengerHistoryService.directKey(19, 7));
    }

    @Test
    void rejectsHistoryForNonMembers() {
        FakeRepository repository = new FakeRepository();
        repository.member = false;
        MessengerHistoryService service = new MessengerHistoryService(repository, 30, 500);

        assertThrows(SecurityException.class, () -> service.loadHistory(9, 7, 0, 30));
    }

    @Test
    void clampsHistoryLimitAndReturnsChronologicalMessages() {
        FakeRepository repository = new FakeRepository();
        repository.rows.add(new MessengerStoredMessage(3, 9, 7, 0, "third", null, 300));
        repository.rows.add(new MessengerStoredMessage(2, 9, 8, 0, "second", null, 200));
        repository.rows.add(new MessengerStoredMessage(1, 9, 7, 0, "first", null, 100));
        MessengerHistoryService service = new MessengerHistoryService(repository, 30, 500);

        MessengerHistoryPage page = service.loadHistory(9, 7, 0, MessengerHistoryService.MAX_PAGE_SIZE + 4_500);

        assertEquals(MessengerHistoryService.MAX_PAGE_SIZE, repository.requestedLimit);
        assertEquals(List.of(1L, 2L, 3L), page.messages().stream().map(MessengerStoredMessage::id).toList());
    }

    @Test
    void cleanupUsesConfiguredAgeAndConversationCap() {
        FakeRepository repository = new FakeRepository();
        MessengerHistoryService service = new MessengerHistoryService(repository, 30, 500);

        service.cleanupRetention();

        assertEquals(30, repository.cleanupDays);
        assertEquals(500, repository.cleanupCap);
    }

    @Test
    void exposesOnlyMembersForAnAuthorizedConversation() {
        FakeRepository repository = new FakeRepository();
        repository.memberIds = List.of(7, 8, 9);
        MessengerHistoryService service = new MessengerHistoryService(repository, 30, 500);

        assertEquals(List.of(7, 8, 9), service.listActiveMemberIds(12, 7));
    }

    private static final class FakeRepository implements MessengerHistoryRepository {
        private boolean member = true;
        private int requestedLimit;
        private int cleanupDays;
        private int cleanupCap;
        private final List<MessengerStoredMessage> rows = new ArrayList<>();
        private List<Integer> memberIds = List.of();

        @Override
        public List<MessengerConversationSummary> listConversations(int userId) {
            return List.of();
        }

        @Override
        public boolean isActiveMember(long conversationId, int userId) {
            return member;
        }

        @Override
        public List<Integer> listActiveMemberIds(long conversationId) {
            return memberIds;
        }

        @Override
        public List<MessengerStoredMessage> loadHistory(long conversationId, int userId, long beforeMessageId, int limit) {
            requestedLimit = limit;
            return new ArrayList<>(rows);
        }

        @Override
        public MessengerStoredMessage storeDirectMessage(int senderId, int recipientId, int type, String message, String metadata) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessengerStoredMessage storeConversationMessage(long conversationId, int senderId, int type, String message, String metadata) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean markRead(long conversationId, int userId, long messageId) {
            return true;
        }

        @Override
        public void cleanupRetention(int days, int maxMessagesPerConversation) {
            cleanupDays = days;
            cleanupCap = maxMessagesPerConversation;
        }
    }
}
