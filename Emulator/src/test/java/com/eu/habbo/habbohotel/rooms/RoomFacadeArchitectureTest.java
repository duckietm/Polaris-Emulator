package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RoomFacadeArchitectureTest {

    private static final Path ROOM_SOURCE = Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/Room.java");

    @Test
    void roomRemainsACompatibilityFacadeInsteadOfRegainingInfrastructureLogic() throws Exception {
        String source = Files.readString(ROOM_SOURCE);

        // 2170: the AIR 13 wired settings added three one-line delegations (roll back, timezone
        // read, three-argument save).
        // 2177: AIR 15 raid protection added the service field, its two constructor lines, the
        // arrival and chat hooks and one accessor. The rule it enforces (a command is not someone
        // talking) lives in the service, not here. Raise this only for delegations, never for logic.
        assertTrue(
                source.lines().count() <= 2177,
                "Keep Room.java below the post-extraction compatibility-facade ceiling");
        assertFalse(source.contains("prepareStatement("), "Database statements belong in collaborators");
        assertFalse(source.matches("(?s).*\"(?:SELECT|INSERT|UPDATE|DELETE) .*"));
    }

    @Test
    void managerOwnedStateIsNotDuplicatedBackIntoRoom() throws Exception {
        String source = Files.readString(ROOM_SOURCE);

        assertFalse(source.contains("private final Set<Game> games;"));
        assertFalse(source.contains("private boolean promoted;"));
        assertFalse(source.contains("private RoomPromotion promotion;"));
        assertFalse(source.contains("private boolean youtubeEnabled"));
        assertFalse(source.contains("private final Object wiredSettingsLock"));
    }
}
