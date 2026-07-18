package com.eu.habbo.monitoring;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmulatorHealthWiringContractTest {

    @Test
    void serverExposesActualTcpListenerState() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/networking/Server.java"));

        assertTrue(source.contains("private volatile boolean listening;"));
        assertTrue(source.contains("this.listening = true;"));
        assertTrue(source.contains("this.listening = false;"));
        assertTrue(source.contains("channelFuture.channel().closeFuture().addListener"));
        assertTrue(source.contains("this.serverChannel.isActive()"));
        assertTrue(source.contains("public boolean isListening()"));
    }

    @Test
    void gameServerExposesActualWebSocketListenerState() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/networking/gameserver/GameServer.java"
        ));

        assertTrue(source.contains("private volatile boolean webSocketListening;"));
        assertTrue(source.contains("this.webSocketListening = true;"));
        assertTrue(source.contains("this.webSocketListening = false;"));
        assertTrue(source.contains("wsFuture.channel().closeFuture().addListener"));
        assertTrue(source.contains("this.webSocketChannel.isActive()"));
        assertTrue(source.contains("public boolean isWebSocketListening()"));
    }

    @Test
    void emuStatsPublishesOneAuthoritativeHealthSnapshot() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/monitoring/EmulatorStatsService.java"
        ));

        assertTrue(source.contains("HealthCheck.unhealthy(\"database\""));
        assertTrue(source.contains("HealthCheck.unhealthy(\"tcp\""));
        assertTrue(source.contains("HealthCheck.unhealthy(\"executor\""));
        assertTrue(source.contains("HealthCheck.unhealthy(\"runtime\""));
        assertTrue(source.contains("HealthCheck.unhealthy(\"websocket\""));
        assertTrue(source.contains("HealthCheck.degraded(\"schedulers\""));
        assertTrue(source.contains("HealthCheck.degraded(\"jvm\""));
        assertTrue(source.contains("public final HealthSnapshot health;"));
        assertTrue(source.contains("health.status.name()"));
    }
}
