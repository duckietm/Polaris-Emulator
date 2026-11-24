package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The hotel default for wired access applies only to a room that never saved its wired settings: a room that saved
 * "nobody" keeps that.
 */
class RoomWiredAccessDefaultTest {
    @Test
    void onlyARoomWithoutASavedRowIsUnsaved() {
        assertTrue(RoomRepository.WiredSettings.defaults().unsaved());
        // The same values read from a saved row are a choice the room made.
        assertFalse(new RoomRepository.WiredSettings(0, 0, "").unsaved());
        assertFalse(new RoomRepository.WiredSettings(8, 8, "UTC").unsaved());
    }

    @Test
    void theSettingIsOffByDefaultAndGivesGroupAdminsBothMasks() throws Exception {
        String service = Files.readString(Path.of("src/main/java/polaris/hotel/rooms/RoomWiredAccessService.java"));

        assertTrue(service.contains("getBoolean(GROUP_ADMINS_DEFAULT_SETTING, false)"));
        assertTrue(service.contains("if (settings.unsaved() && groupAdminsByDefault()) {"));
        assertTrue(service.contains("this.inspectMask = Room.WIRED_ACCESS_GROUP_ADMINS;"));
        assertTrue(service.contains("this.modifyMask = Room.WIRED_ACCESS_GROUP_ADMINS;"));
    }
}
