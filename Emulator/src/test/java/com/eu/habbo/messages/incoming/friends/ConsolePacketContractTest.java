package com.eu.habbo.messages.incoming.friends;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConsolePacketContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void consolePacketHeadersMatchRendererContract() throws Exception {
        String incoming = source("com/eu/habbo/messages/incoming/Incoming.java");
        String outgoing = source("com/eu/habbo/messages/outgoing/Outgoing.java");

        assertTrue(incoming.contains("MarkConsoleReadEvent = 4085"));
        assertTrue(incoming.contains("ConsoleTypingEvent = 4087"));
        assertTrue(incoming.contains("RefreshFriendListEvent = 1419"));
        assertTrue(outgoing.contains("FriendIsTypingComposer = 4088"));
        assertTrue(outgoing.contains("ConsoleReadReceiptComposer = 4086"));
    }

    @Test
    void packetManagerRegistersEveryConsoleHandler() throws Exception {
        String registry = source("com/eu/habbo/messages/PacketManager.java");

        assertTrue(registry.contains("Incoming.MarkConsoleReadEvent, MarkConsoleReadEvent.class"));
        assertTrue(registry.contains("Incoming.ConsoleTypingEvent, ConsoleTypingEvent.class"));
        assertTrue(registry.contains("Incoming.RefreshFriendListEvent, RefreshFriendListEvent.class"));
    }

    @Test
    void typingOnlyReachesAFriendWhoIsOnline() throws Exception {
        String typing = source("com/eu/habbo/messages/incoming/friends/ConsoleTypingEvent.java");

        assertTrue(typing.contains("getMessenger().getFriend(peerId) == null"));
        assertTrue(typing.contains("peer == null || peer.getClient() == null"));
        assertTrue(typing.contains("new FriendIsTypingComposer(self.getHabboInfo().getId(), typing)"));
        assertTrue(typing.contains("return 250;"));
    }

    @Test
    void markingReadResolvesTheDirectConversationWithThatPeer() throws Exception {
        String read = source("com/eu/habbo/messages/incoming/friends/MarkConsoleReadEvent.java");

        assertTrue(read.contains("summary.type() == ConversationType.DIRECT"));
        assertTrue(read.contains("summary.participantId() == peerId"));
        assertTrue(read.contains("history.markRead(conversation.id(), userId, conversation.lastMessageId())"));
        assertTrue(read.contains("new MessengerReadCursorComposer("));
        assertTrue(read.contains("memberId == peerId"));
        assertTrue(read.contains("new ConsoleReadReceiptComposer(userId)"));
    }

    @Test
    void theRefreshAnswersWithEveryFriendAndIsRateLimited() throws Exception {
        String refresh = source("com/eu/habbo/messages/incoming/friends/RefreshFriendListEvent.java");

        assertTrue(refresh.contains("habbo, habbo.getMessenger().getFriends().values(), 0"));
        assertTrue(refresh.contains("return 5000;"));
    }
}
