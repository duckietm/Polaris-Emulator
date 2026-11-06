package polaris.monitoring;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EmulatorHealthWiringContractTest {

    @Test
    void serverExposesActualTcpListenerState() throws Exception {
        String source = Files.readString(Path.of("src/main/java/polaris/networking/Server.java"));

        assertTrue(source.contains("private volatile boolean listening;"));
        assertTrue(source.contains("this.listening = true;"));
        assertTrue(source.contains("this.listening = false;"));
        assertTrue(source.contains("channelFuture.channel().closeFuture().addListener"));
        assertTrue(source.contains("this.serverChannel.isActive()"));
        assertTrue(source.contains("public boolean isListening()"));
    }

    @Test
    void gameServerExposesActualWebSocketListenerState() throws Exception {
        String source = Files.readString(Path.of("src/main/java/polaris/networking/gameserver/GameServer.java"));

        assertTrue(source.contains("private volatile boolean webSocketListening;"));
        assertTrue(source.contains("this.webSocketListening = true;"));
        assertTrue(source.contains("this.webSocketListening = false;"));
        assertTrue(source.contains("wsFuture.channel().closeFuture().addListener"));
        assertTrue(source.contains("this.webSocketChannel.isActive()"));
        assertTrue(source.contains("public boolean isWebSocketListening()"));
    }

    @Test
    void emuStatsPublishesOneAuthoritativeHealthSnapshot() throws Exception {
        String source = Files.readString(Path.of("src/main/java/polaris/monitoring/EmulatorStatsService.java"));
        String compactSource = source.replaceAll("\\s+", "");

        assertTrue(compactSource.contains("HealthCheck.unhealthy(\"database\""));
        assertTrue(compactSource.contains("HealthCheck.unhealthy(\"tcp\""));
        assertTrue(compactSource.contains("HealthCheck.unhealthy(\"executor\""));
        assertTrue(compactSource.contains("HealthCheck.unhealthy(\"runtime\""));
        assertTrue(compactSource.contains("HealthCheck.unhealthy(\"websocket\""));
        assertTrue(compactSource.contains("HealthCheck.degraded(\"schedulers\""));
        assertTrue(compactSource.contains("HealthCheck.degraded(\"jvm\""));
        assertTrue(source.contains("public final HealthSnapshot health;"));
        assertTrue(source.contains("health.status.name()"));
    }
}
