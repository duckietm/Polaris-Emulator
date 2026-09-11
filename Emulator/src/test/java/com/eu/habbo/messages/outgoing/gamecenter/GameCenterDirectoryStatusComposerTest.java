package com.eu.habbo.messages.outgoing.gamecenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GameCenterDirectoryStatusComposerTest {
    @Test
    void encodesTheFourCountersInRendererOrder() {
        var packet = new GameCenterDirectoryStatusComposer(GameCenterDirectoryStatusComposer.STATUS_OK, 0, 0, 7)
                .compose()
                .get();
        packet.skipBytes(6);

        assertEquals(GameCenterDirectoryStatusComposer.STATUS_OK, packet.readInt());
        assertEquals(0, packet.readInt());
        assertEquals(0, packet.readInt());
        assertEquals(7, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void theHubAsksForItOnEveryOpen() throws Exception {
        assertTrue(Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/Incoming.java"))
                .contains("GameCenterCheckDirectoryStatusEvent = 3259"));
        assertTrue(Files.readString(Path.of("src/main/java/com/eu/habbo/messages/PacketManager.java"))
                .contains("Incoming.GameCenterCheckDirectoryStatusEvent, GameCenterCheckDirectoryStatusEvent.class"));
        assertTrue(Files.readString(
                        Path.of(
                                "src/main/java/com/eu/habbo/messages/incoming/gamecenter/GameCenterCheckDirectoryStatusEvent.java"))
                .contains("SnowWarManager.getInstance()"));
    }
}
