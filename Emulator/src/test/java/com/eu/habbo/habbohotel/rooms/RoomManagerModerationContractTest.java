package com.eu.habbo.habbohotel.rooms;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomManagerModerationContractTest {
    @Test
    void roomBanCannotTargetRoomOwner() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomManager.java"));

        int rightsGuard = source.indexOf("rights != null && !room.hasRights(rights)");
        int ownerGuard = source.indexOf("room.getOwnerId() == userId");
        int banCreate = source.indexOf("new RoomBan(roomId, userId");

        assertTrue(rightsGuard > -1, "room bans must require rights");
        assertTrue(ownerGuard > rightsGuard, "room bans must guard owner targets after rights are checked");
        assertTrue(ownerGuard < banCreate, "room owner must be rejected before a RoomBan is created");
    }
}
