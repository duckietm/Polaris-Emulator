package com.eu.habbo.networking.gameserver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GamePacketExecutionContractTest {

    @Test
    void tcpAndWebSocketPipelinesUseTheSameOffIoExecutionGroup() throws Exception {
        String tcp = source("GameServer.java");
        String webSocket = source("WebSocketChannelInitializer.java");

        assertTrue(
                tcp.contains("GamePacketExecutionGroup.get()"), "TCP packet handlers must be off the Netty I/O loop");
        assertTrue(
                webSocket.contains("GamePacketExecutionGroup.get()"),
                "WebSocket packet handlers must use the shared execution group");
        assertLatencyProbeOrder(tcp);
        assertLatencyProbeOrder(webSocket);
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

    private static void assertLatencyProbeOrder(String pipelineSource) {
        int marker = pipelineSource.indexOf("new PacketDispatchMarker()");
        int latency = pipelineSource.indexOf("\"packetDispatchLatency\"");
        int handler = pipelineSource.indexOf("\"gameMessageHandler\"");

        assertTrue(marker >= 0);
        assertTrue(latency > marker, "dispatch latency must start after the I/O marker");
        assertTrue(handler > latency, "dispatch latency must be observed before packet handling");
    }
}
