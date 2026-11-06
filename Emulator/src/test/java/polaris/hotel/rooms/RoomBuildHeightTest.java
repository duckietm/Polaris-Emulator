package polaris.hotel.rooms;

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
        String helper = source("polaris/hotel/rooms/RoomBuildHeight.java");

        assertTrue(helper.contains("!actor.getRoomUnit().isBuildHeightEnabled()"));
        assertTrue(helper.contains("double floor = layout.getHeightAtSquare(tile.x, tile.y);"));
        assertTrue(
                helper.contains("Math.min(floor + actor.getRoomUnit().getBuildHeight(), Room.MAXIMUM_FURNI_HEIGHT)"));
    }

    @Test
    void placingAndMovingBothGoThroughIt() throws Exception {
        assertTrue(source("polaris/hotel/rooms/RoomItemPlacementService.java")
                .contains("RoomBuildHeight.apply(owner, layout, tile, height)"));
        assertTrue(source("polaris/hotel/rooms/RoomItemMovementService.java")
                .contains("RoomBuildHeight.apply(actor, layout, tile, z)"));
    }

    @Test
    void theWidgetIsOfferedWithTheRightsAndWithdrawnWithThem() throws Exception {
        String rights = source("polaris/hotel/rooms/RoomRightsManager.java");

        assertTrue(rights.contains("boolean mayBuild = !flatCtrl.equals(RoomRightLevels.NONE);"));
        assertTrue(rights.contains("new BuildHeightAvailableComposer(mayBuild)"));
        assertTrue(rights.contains("user.getRoomUnit().setBuildHeight(false, 0.0D);"));
    }

    @Test
    void theHandlerRefusesSomebodyWhoCannotBuildAndClampsTheHeight() throws Exception {
        String handler = source("polaris/messages/incoming/rooms/items/SetBuildHeightEvent.java");

        assertTrue(handler.contains("if (enabled && !room.hasRights(this.client.getUser())) return;"));
        assertTrue(handler.contains("this.packet.readInt() / HEIGHT_SCALE"));
        assertTrue(handler.contains("Math.max(0.0D, Math.min(height, Room.MAXIMUM_FURNI_HEIGHT))"));
    }

    @Test
    void headersMatchTheRendererContract() throws Exception {
        assertTrue(source("polaris/messages/incoming/Incoming.java").contains("SetBuildHeightEvent = 9351"));
        assertTrue(source("polaris/messages/outgoing/Outgoing.java").contains("BuildHeightAvailableComposer = 9350"));
        assertTrue(source("polaris/messages/PacketManager.java")
                .contains("Incoming.SetBuildHeightEvent, SetBuildHeightEvent.class"));
    }
}
