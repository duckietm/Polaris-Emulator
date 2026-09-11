package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RoomBuildHeightTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void theModeOnlyChangesTheHeightAndKeepsTheCeiling() throws Exception {
        String helper = source("com/eu/habbo/habbohotel/rooms/RoomBuildHeight.java");

        assertTrue(helper.contains("!actor.getRoomUnit().isBuildHeightEnabled()"));
        assertTrue(helper.contains("double floor = layout.getHeightAtSquare(tile.x, tile.y);"));
        assertTrue(
                helper.contains("Math.min(floor + actor.getRoomUnit().getBuildHeight(), Room.MAXIMUM_FURNI_HEIGHT)"));
    }

    @Test
    void placingAndMovingBothGoThroughIt() throws Exception {
        assertTrue(source("com/eu/habbo/habbohotel/rooms/RoomItemPlacementService.java")
                .contains("RoomBuildHeight.apply(owner, layout, tile, height)"));
        assertTrue(source("com/eu/habbo/habbohotel/rooms/RoomItemMovementService.java")
                .contains("RoomBuildHeight.apply(actor, layout, tile, z)"));
    }

    @Test
    void theWidgetIsOfferedWithTheRightsAndWithdrawnWithThem() throws Exception {
        String rights = source("com/eu/habbo/habbohotel/rooms/RoomRightsManager.java");

        assertTrue(rights.contains("boolean mayBuild = !flatCtrl.equals(RoomRightLevels.NONE);"));
        assertTrue(rights.contains("new BuildHeightAvailableComposer(mayBuild)"));
        assertTrue(rights.contains("habbo.getRoomUnit().setBuildHeight(false, 0.0D);"));
    }

    @Test
    void theHandlerRefusesSomebodyWhoCannotBuildAndClampsTheHeight() throws Exception {
        String handler = source("com/eu/habbo/messages/incoming/rooms/items/SetBuildHeightEvent.java");

        assertTrue(handler.contains("if (enabled && !room.hasRights(this.client.getHabbo())) return;"));
        assertTrue(handler.contains("this.packet.readInt() / HEIGHT_SCALE"));
        assertTrue(handler.contains("Math.max(0.0D, Math.min(height, Room.MAXIMUM_FURNI_HEIGHT))"));
    }

    @Test
    void headersMatchTheRendererContract() throws Exception {
        assertTrue(source("com/eu/habbo/messages/incoming/Incoming.java").contains("SetBuildHeightEvent = 9351"));
        assertTrue(
                source("com/eu/habbo/messages/outgoing/Outgoing.java").contains("BuildHeightAvailableComposer = 9350"));
        assertTrue(source("com/eu/habbo/messages/PacketManager.java")
                .contains("Incoming.SetBuildHeightEvent, SetBuildHeightEvent.class"));
    }
}
