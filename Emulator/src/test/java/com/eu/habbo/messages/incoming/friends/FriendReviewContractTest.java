package com.eu.habbo.messages.incoming.friends;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards for the friends and messenger review fixes, in the source style of FriendBatchGuardContractTest. */
class FriendReviewContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/" + path + ".java"));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start > -1, "missing " + signature);
        int end = source.indexOf("\n    }\n", start);
        return source.substring(start, end);
    }

    @Test
    void acceptingCountsOnlyTheIncomingRequest() throws Exception {
        String accept = method(
                source("habbohotel/messenger/Messenger"), "public void acceptFriendRequest(int userFrom, int userTo)");

        int incoming = accept.indexOf("DELETE FROM messenger_friendrequests WHERE user_to_id = ? AND user_from_id = ?");
        int insert = accept.indexOf("INSERT IGNORE INTO messenger_friendships");

        assertTrue(incoming > -1, "accept must delete the request sent to the accepting user");
        assertTrue(accept.indexOf("executeUpdate() <= 0) return;") > incoming, "no incoming request, no friendship");
        assertTrue(insert > incoming, "the friendship is stored only after the incoming request was found");
        assertFalse(accept.contains("deleteFriendRequests("), "the two-way delete let users accept their own requests");
    }

    @Test
    void storedConversationsStillRequireAFriendAndFilterText() throws Exception {
        String event = source("messages/incoming/friends/SendMessengerMessageEvent");

        int members = event.indexOf("history.listActiveMemberIds(conversationId, senderId)");
        int notFriends = event.indexOf("throw new SecurityException(\"not friends\")", members);
        int filter = event.indexOf("environment.getWordFilter().filter(message, client.getHabbo())");
        int store = event.indexOf("history.sendMessage(");

        assertTrue(
                members > -1 && notFriends > members, "every other member of a stored conversation must be a friend");
        assertTrue(filter > -1 && filter < store, "text is word filtered before it is stored and delivered");
    }

    @Test
    void initSendsTheEnforcedFriendLimitFirst() throws Exception {
        String composer = source("messages/outgoing/friends/MessengerInitComposer");
        int own = composer.indexOf("appendInt(RankLimits.friends(this.habbo))");
        int normal = composer.indexOf("appendInt(RankLimits.friendsShown(this.habbo, false))");

        assertTrue(own > -1 && own < normal, "MessengerInitParser reads the user's own limit first");
    }

    @Test
    void bulkAcceptDoesNotLoadWholeAccountsAndResendsRefusedRequests() throws Exception {
        String accept = source("messages/incoming/friends/AcceptFriendRequestEvent");

        assertFalse(accept.contains("new Habbo("), "offline targets are read with one small query");
        assertTrue(accept.contains("reloadFriendRequests(habbo)"), "refused requests are sent back to the client");
        assertTrue(accept.contains("new LoadFriendRequestsComposer(habbo)"));
    }

    @Test
    void requestListComesFromTheDatabaseAndSearchIsThrottled() throws Exception {
        assertTrue(
                source("messages/incoming/friends/RequestFriendRequestsEvent").contains("reloadFriendRequests("));

        String search = source("messages/incoming/friends/SearchUserEvent");
        assertTrue(search.contains("getHabboStats().lastUsersSearched = now"), "the search throttle must be set");
    }

    @Test
    void consoleTextIsKeptInTheConversationHistory() throws Exception {
        String buddy = source("habbohotel/messenger/MessengerBuddy");
        String delivery =
                method(buddy, "public boolean onMessageReceivedWithDeliveryStatus(Habbo from, String message)");

        int filter = delivery.indexOf("getWordFilter().filter(");
        int store = delivery.indexOf("MessengerHistoryServices.create()");

        assertTrue(store > filter, "the filtered console text is stored in the conversation history");

        String repository = source("habbohotel/messenger/history/JdbcMessengerHistoryRepository");
        assertTrue(repository.contains("adoptLegacyDirectHistory(connection, conversationId, senderId, recipientId);"));
    }
}
