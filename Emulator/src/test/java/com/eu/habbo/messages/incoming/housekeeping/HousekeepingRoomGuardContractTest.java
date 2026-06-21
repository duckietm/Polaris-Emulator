package com.eu.habbo.messages.incoming.housekeeping;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HousekeepingRoomGuardContractTest {
    @Test
    void destructiveRoomActionsRespectOwnerRankCeiling() throws Exception {
        Path base = Path.of("src/main/java/com/eu/habbo/messages/incoming/housekeeping");

        for (String handler : List.of(
                "HousekeepingDeleteRoomEvent.java",
                "HousekeepingKickAllFromRoomEvent.java",
                "HousekeepingMuteRoomEvent.java",
                "HousekeepingRoomStateEvent.java",
                "HousekeepingTransferRoomOwnershipEvent.java"
        )) {
            String source = Files.readString(base.resolve(handler));

            assertTrue(source.contains("HousekeepingRoomGuard.canManageRoom(this.client.getHabbo(), room)"),
                    handler + " must reject room mutations when the room owner is peer-or-higher ranked");
            assertTrue(source.contains("housekeeping.error.rank_too_high"),
                    handler + " must surface a rank-ceiling error for protected room owners");
        }
    }

    @Test
    void roomGuardDelegatesToTargetRankGuard() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/housekeeping/HousekeepingRoomGuard.java"));

        assertTrue(source.contains("HousekeepingTargetRankGuard.canTargetUser(operator, room.getOwnerId())"),
                "room-owner checks must use the same core-rank peer override as user moderation");
    }

    @Test
    void roomMuteRejectsNegativeDurations() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/housekeeping/HousekeepingMuteRoomEvent.java"));

        assertTrue(source.contains("minutes < 0"),
                "room mute should reject negative duration values instead of treating them as unmute");
    }
}
