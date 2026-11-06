package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.WiredPlatform;
import polaris.hotel.GameEnvironment;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.protocol.WiredActionCodes;

/** The official "move as group" settings, sources and the load of a box saved by the old element. */
class WiredEffectMoveAsGroupTest {

    @Test
    void itSavesTheOfficialSettingsAndSources() throws Exception {
        WiredEffectMoveAsGroup box = new WiredEffectMoveAsGroup(1, 1, mock(Item.class), "", 0, 0);
        box.setRoomId(7);
        GameEnvironment environment = mock(GameEnvironment.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(7)).thenReturn(mock(Room.class));
        WiredSettings settings = new WiredSettings(new int[] {0, 70, -3}, "", new int[0], -1);
        settings.setFurniSourceTypes(new int[] {WiredSourceUtil.SOURCE_SELECTOR, WiredSourceUtil.SOURCE_SIGNAL});
        settings.setUserSourceTypes(new int[] {WiredSourceUtil.SOURCE_CLICKED_USER});

        try (MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class)) {
            platform.when(WiredPlatform::gameEnvironment).thenReturn(environment);
            assertTrue(box.saveData(settings, null));
        }
        assertArrayEquals(
                new int[] {WiredSourceUtil.SOURCE_SELECTOR, WiredSourceUtil.SOURCE_SIGNAL}, box.officialFurniSources());
        assertArrayEquals(new int[] {WiredSourceUtil.SOURCE_CLICKED_USER}, box.officialUserSources());
        String saved = box.getWiredData();
        assertTrue(saved.contains("\"targetIsUser\":false"), saved);
        assertTrue(saved.contains("\"offsetX\":64"), "offsets are clamped to the official -64..64: " + saved);
        assertTrue(saved.contains("\"offsetY\":-3"), saved);
    }

    @Test
    void aBoxSavedByTheOldStepElementKeepsItsPicks() throws Exception {
        Room room = mock(Room.class);
        UserItem picked = mock(UserItem.class);
        when(picked.getId()).thenReturn(42);
        when(room.getUserItem(anyInt())).thenReturn(null);
        when(room.getUserItem(42)).thenReturn(picked);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("wired_data"))
                .thenReturn("{\"direction\":2,\"delay\":1,\"itemIds\":[42],\"furniSource\":100}");

        WiredEffectMoveAsGroup box = new WiredEffectMoveAsGroup(1, 1, mock(Item.class), "", 0, 0);
        box.loadWiredData(row, room);

        assertArrayEquals(
                new int[] {WiredSourceUtil.SOURCE_SELECTED, WiredSourceUtil.SOURCE_TRIGGER},
                box.officialFurniSources());
        assertTrue(box.getWiredData().contains("\"itemIds\":[42]"), box.getWiredData());
        assertTrue(box.getWiredData().contains("\"targetIsUser\":true"), box.getWiredData());
    }

    @Test
    void theOfficialBoxIs57AndOurStepBoxIsCustom() {
        assertEquals(57, WiredActionCodes.official(WiredEffectType.MOVE_FURNI_AS_GROUP.code));
        assertEquals(1154, WiredActionCodes.official(WiredEffectType.MOVE_FURNI_GROUP_STEP.code));
        assertEquals(WiredEffectType.MOVE_FURNI_GROUP_STEP, WiredEffectMoveFurniAsGroup.type);
    }
}
