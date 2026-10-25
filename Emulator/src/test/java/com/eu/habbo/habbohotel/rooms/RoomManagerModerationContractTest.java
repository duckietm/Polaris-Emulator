package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RoomManagerModerationContractTest {
    @Test
    void roomBanCannotTargetRoomOwner() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomModerationService.java"));

        int rightsGuard = source.indexOf("rights != null && !room.getRightsManager().canBan(rights)");
        int ownerGuard = source.indexOf("room.getOwnerId() == userId");
        int banCreate = source.indexOf("new RoomBan(");

        assertTrue(rightsGuard > -1, "room bans must follow the room's ban setting");
        assertTrue(ownerGuard > rightsGuard, "room bans must guard owner targets after rights are checked");
        assertTrue(ownerGuard < banCreate, "room owner must be rejected before a RoomBan is created");
    }
}
