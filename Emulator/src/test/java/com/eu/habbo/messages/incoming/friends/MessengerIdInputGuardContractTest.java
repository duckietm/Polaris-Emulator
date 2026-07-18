package com.eu.habbo.messages.incoming.friends;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MessengerIdInputGuardContractTest {
    private static final Path FRIEND_SOURCE = Path.of("src/main/java/com/eu/habbo/messages/incoming/friends");

    private static String friendSource(String file) throws Exception {
        return Files.readString(FRIEND_SOURCE.resolve(file));
    }

    @Test
    void messengerPacketIdsUseTheSharedPositiveIdGuard() throws Exception {
        Map<String, String> expectedGuards = Map.ofEntries(
                Map.entry("ChangeRelationEvent.java", "FriendInputGuard.isPositiveId(userId)"),
                Map.entry("DeclineFriendRequestEvent.java", "FriendInputGuard.isPositiveId(userId)"),
                Map.entry("FriendPrivateMessageEvent.java", "FriendInputGuard.isPositiveId(userId)"),
                Map.entry("InviteFriendsEvent.java", "FriendInputGuard.isPositiveId(i)"),
                Map.entry("MarkMessengerReadEvent.java", "FriendInputGuard.arePositiveIds(conversationId, messageId)"),
                Map.entry("RemoveFriendCategoryEvent.java", "FriendInputGuard.isPositiveId(categoryId)"),
                Map.entry("RenameFriendCategoryEvent.java", "FriendInputGuard.isPositiveId(categoryId)"),
                Map.entry("RequestMessengerHistoryEvent.java", "FriendInputGuard.isPositiveId(conversationId)"),
                Map.entry("SendMessengerMessageEvent.java", "FriendInputGuard.isValidMessageTarget(conversationId, recipientId)"),
                Map.entry("StalkFriendEvent.java", "FriendInputGuard.isPositiveId(friendId)"));

        for (Map.Entry<String, String> entry : expectedGuards.entrySet()) {
            assertTrue(friendSource(entry.getKey()).contains(entry.getValue()),
                    entry.getKey() + " must reject invalid packet IDs before lookup or persistence");
        }
    }

    @Test
    void readCursorMustReferenceAMessageInTheSameConversation() throws Exception {
        String repository = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/messenger/history/JdbcMessengerHistoryRepository.java"));
        int markRead = repository.indexOf("public boolean markRead");
        String method = repository.substring(markRead, repository.indexOf("@Override", markRead + 10));

        assertTrue(method.contains("EXISTS"), "cursor update must atomically verify the message");
        assertTrue(method.contains("message.id = ?"), "cursor update must validate the supplied message id");
        assertTrue(method.contains("message.conversation_id = member.conversation_id"),
                "cursor message must belong to the member conversation");
    }
}
