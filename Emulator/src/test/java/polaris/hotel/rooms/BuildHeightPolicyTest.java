package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The rank permission and the height range of the build height widget, which the BuildHeight plugin
 * used to add and which now live in core under the plugin's own names.
 */
class BuildHeightPolicyTest {

    @Test
    void withoutSettingsTheRangeIsTheFloorUpToTheCeiling() {
        assertEquals(0, BuildHeightPolicy.minHeight("", 40d));
        assertEquals(0, BuildHeightPolicy.minHeight(null, 40d));
        assertEquals(40, BuildHeightPolicy.maxHeight("", 40d));
        assertEquals(40, BuildHeightPolicy.maxHeight("not a number", 40d));
    }

    @Test
    void aHotelCanAllowBuildingBelowTheFloorAndLowerTheTop() {
        assertEquals(-40, BuildHeightPolicy.minHeight("-40", 40d));
        assertEquals(-5, BuildHeightPolicy.minHeight(" -5 ", 40d));
        assertEquals(20, BuildHeightPolicy.maxHeight("20", 40d));
    }

    @Test
    void theRangeNeverLeavesWhatFurnitureCanStandAt() {
        // The bottom is never above the floor and never deeper than the ceiling is high.
        assertEquals(0, BuildHeightPolicy.minHeight("5", 40d));
        assertEquals(-40, BuildHeightPolicy.minHeight("-500", 40d));
        // The top is never below the floor and never above the ceiling.
        assertEquals(40, BuildHeightPolicy.maxHeight("500", 40d));
        assertEquals(0, BuildHeightPolicy.maxHeight("-3", 40d));
    }

    @Test
    void aRequestedHeightIsKeptInsideTheRange() {
        assertEquals(2.5d, BuildHeightPolicy.clamp(2.5d, 0, 40));
        assertEquals(0d, BuildHeightPolicy.clamp(-3d, 0, 40));
        assertEquals(-3d, BuildHeightPolicy.clamp(-3d, -40, 40));
        assertEquals(40d, BuildHeightPolicy.clamp(99d, -40, 40));
        assertEquals(0d, BuildHeightPolicy.clamp(Double.NaN, -40, 40));
    }

    @Test
    void nobodyWithoutBuildRightsMayUseIt() {
        assertFalse(BuildHeightPolicy.mayUse(null, true));
        assertFalse(BuildHeightPolicy.mayUse(null, false));
    }

    @Test
    void thePermissionIsNewForEveryRankButLeavesAHotelsOwnChoiceAlone() throws Exception {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V20261121090000__build_height_permission.sql"));

        assertTrue(migration.contains("'cmd_buildheight'"));
        assertTrue(migration.contains("ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`)"));
        assertTrue(migration.contains("@buildheight_known > 0"), "ranks are granted only when the row is new");
        // The plugin's setting names are kept, and no range row is written: absent means the old range.
        assertEquals("building.cmd_buildheight.min_height", BuildHeightPolicy.MIN_KEY);
        assertEquals("building.cmd_buildheight.max_height", BuildHeightPolicy.MAX_KEY);
        assertFalse(migration.contains("emulator_settings"));
    }

    @Test
    void theOfferTheHandlerAndThePacketAllUseThePolicy() throws Exception {
        String rights = Files.readString(Path.of("src/main/java/polaris/hotel/rooms/RoomRightsManager.java"));
        String handler = Files.readString(
                Path.of("src/main/java/polaris/messages/incoming/rooms/items/SetBuildHeightEvent.java"));
        String composer = Files.readString(
                Path.of("src/main/java/polaris/messages/outgoing/rooms/BuildHeightAvailableComposer.java"));

        assertTrue(rights.contains("BuildHeightPolicy.mayUse(user, !flatCtrl.equals(RoomRightLevels.NONE))"));
        assertTrue(handler.contains("BuildHeightPolicy.mayUse("));
        assertTrue(handler.contains("BuildHeightPolicy.clamp("));
        assertTrue(composer.contains("appendInt(BuildHeightPolicy.minHeight())"));
        assertTrue(composer.contains("appendInt(BuildHeightPolicy.maxHeight())"));
    }
}
