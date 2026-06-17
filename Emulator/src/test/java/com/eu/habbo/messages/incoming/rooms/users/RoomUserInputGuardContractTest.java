package com.eu.habbo.messages.incoming.rooms.users;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomUserInputGuardContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/users/" + name + ".java"));
    }

    @Test
    void roomModerationHandlersRejectInvalidUserAndRoomIds() throws Exception {
        for (String handler : new String[]{"RoomUserBanEvent", "UnbanRoomUserEvent", "RoomUserMuteEvent"}) {
            String source = source(handler);
            int userRead = source.indexOf("int userId = this.packet.readInt()");
            int roomRead = source.indexOf("int roomId = this.packet.readInt()", userRead);
            int guard = source.indexOf("RoomUserInputGuard.isPositiveId(userId)", roomRead);
            int roomLookup = source.indexOf("getCurrentRoom()", guard);

            assertTrue(userRead > -1 && roomRead > userRead, handler + " should read user and room ids");
            assertTrue(guard > roomRead && guard < roomLookup,
                    handler + " should reject invalid ids before resolving room state");
        }
    }

    @Test
    void rightsMutationHandlersRejectInvalidUserIds() throws Exception {
        String giveRights = source("RoomUserGiveRightsEvent");
        String removeRights = source("RoomUserRemoveRightsEvent");

        int giveRead = giveRights.indexOf("int userId = this.packet.readInt()");
        int giveGuard = giveRights.indexOf("RoomUserInputGuard.isPositiveId(userId)", giveRead);
        int giveTarget = giveRights.indexOf("room.getHabbo(userId)", giveGuard);

        int removeRead = removeRights.indexOf("int userId = this.packet.readInt()");
        int removeGuard = removeRights.indexOf("RoomUserInputGuard.isPositiveId(userId)", removeRead);
        int removeCall = removeRights.indexOf("room.removeRights(userId)", removeGuard);

        assertTrue(giveGuard > giveRead && giveGuard < giveTarget,
                "give-rights should validate target id before online/friend lookups");
        assertTrue(removeGuard > removeRead && removeGuard < removeCall,
                "remove-rights should skip invalid ids before removing rights");
    }

    @Test
    void kickPluginEventOnlyFiresAfterPermissionCheck() throws Exception {
        String source = source("RoomUserKickEvent");

        int userRead = source.indexOf("int userId = this.packet.readInt()");
        int idGuard = source.indexOf("RoomUserInputGuard.isPositiveId(userId)", userRead);
        int targetLookup = source.indexOf("room.getHabbo(userId)", idGuard);
        int permissionCheck = source.indexOf("room.hasRights(this.client.getHabbo())", targetLookup);
        int event = source.indexOf("new UserKickEvent", permissionCheck);
        int kick = source.indexOf("room.kickHabbo(target, true)", event);

        assertTrue(idGuard > userRead && idGuard < targetLookup,
                "kick should validate target id before room lookup");
        assertTrue(permissionCheck > targetLookup && event > permissionCheck && event < kick,
                "kick plugin event should only fire once the actor is authorized");
    }

    @Test
    void roomActionsRejectUnknownActionIdsBeforeComposersAndWired() throws Exception {
        String source = source("RoomUserActionEvent");

        int actionRead = source.indexOf("int action = this.packet.readInt()");
        int guard = source.indexOf("RoomUserInputGuard.isValidAction(action)", actionRead);
        int composer = source.indexOf("new RoomUserActionComposer", guard);
        int wired = source.indexOf("WiredManager.triggerUserPerformsAction", guard);

        assertTrue(guard > actionRead && guard < composer,
                "room actions should reject unknown ids before composing room state");
        assertTrue(guard < wired,
                "room actions should reject unknown ids before wired triggers");
    }

    @Test
    void helperBoundsExpectedRanges() {
        assertFalse(RoomUserInputGuard.isPositiveId(0));
        assertTrue(RoomUserInputGuard.isPositiveId(1));
        assertFalse(RoomUserInputGuard.isValidAction(-1));
        assertTrue(RoomUserInputGuard.isValidAction(0));
        assertTrue(RoomUserInputGuard.isValidAction(7));
        assertFalse(RoomUserInputGuard.isValidAction(8));
    }
}
