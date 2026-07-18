package com.eu.habbo.networking.gameserver;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GamePacketExecutionContractTest {

    @Test
    void tcpAndWebSocketPipelinesUseTheSameOffIoExecutionGroup() throws Exception {
        String tcp = source("GameServer.java");
        String webSocket = source("WebSocketChannelInitializer.java");

        assertTrue(tcp.contains("GamePacketExecutionGroup.get()"), "TCP packet handlers must be off the Netty I/O loop");
        assertTrue(webSocket.contains("GamePacketExecutionGroup.get()"), "WebSocket packet handlers must use the shared execution group");
    }

    @Test
    void gameServerAlwaysShutsDownTheSharedPacketExecutionGroup() throws Exception {
        String tcp = source("GameServer.java");
        int stopMethod = tcp.indexOf("public void stop()");
        int shutdown = tcp.indexOf("GamePacketExecutionGroup.shutdown()", stopMethod);

        assertTrue(stopMethod >= 0);
        assertTrue(shutdown > stopMethod, "the packet executor must stop for TCP-only and WebSocket servers");
    }

    private static String source(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/networking/gameserver/" + file));
    }
}
