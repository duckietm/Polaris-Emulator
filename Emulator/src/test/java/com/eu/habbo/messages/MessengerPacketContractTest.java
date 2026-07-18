package com.eu.habbo.messages;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MessengerPacketContractTest {
    @Test
    void messengerHeadersUseReservedDirectionalIds() throws Exception {
        String incoming = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/Incoming.java"));
        String outgoing = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/outgoing/Outgoing.java"));

        assertTrue(incoming.contains("RequestMessengerConversationsEvent = 4900"));
        assertTrue(incoming.contains("RequestMessengerHistoryEvent = 4901"));
        assertTrue(incoming.contains("SendMessengerMessageEvent = 4902"));
        assertTrue(incoming.contains("MarkMessengerReadEvent = 4903"));
        assertTrue(outgoing.contains("MessengerConversationsComposer = 4900"));
        assertTrue(outgoing.contains("MessengerHistoryComposer = 4901"));
        assertTrue(outgoing.contains("MessengerMessageAckComposer = 4902"));
        assertTrue(outgoing.contains("MessengerMessageFailedComposer = 4903"));
        assertTrue(outgoing.contains("MessengerMessageComposer = 4904"));
        assertTrue(outgoing.contains("MessengerReadCursorComposer = 4905"));
    }

    @Test
    void packetManagerRegistersMessengerHandlers() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/PacketManager.java"));

        assertTrue(source.contains("Incoming.RequestMessengerConversationsEvent, RequestMessengerConversationsEvent.class"));
        assertTrue(source.contains("Incoming.RequestMessengerHistoryEvent, RequestMessengerHistoryEvent.class"));
        assertTrue(source.contains("Incoming.SendMessengerMessageEvent, SendMessengerMessageEvent.class"));
        assertTrue(source.contains("Incoming.MarkMessengerReadEvent, MarkMessengerReadEvent.class"));
    }
}
