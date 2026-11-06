package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.hotel.GameEnvironment;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomManager;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredSourceUtil;

/**
 * A toggle box keeps its picked furni whatever its furni source, as the official editor does: with a
 * selector as the source the picks are unused, but saving must not throw them away.
 */
class WiredEffectToggleFurniPicksTest {

    @Test
    void picksSurviveASaveWithTheSelectorAsSource() throws Exception {
        Room room = mock(Room.class);
        UserItem lamp = mock(UserItem.class);
        when(lamp.getId()).thenReturn(1234);
        when(room.getUserItem(1234)).thenReturn(lamp);

        GameEnvironment environment = mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS);
        RoomManager rooms = mock(RoomManager.class);
        when(environment.getRoomManager()).thenReturn(rooms);
        when(rooms.getRoom(7)).thenReturn(room);
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(anyString())).thenReturn(20);
        when(config.getInt(anyString(), anyInt())).thenReturn(120);

        Item base = mock(Item.class);
        WiredEffectToggleFurni toggle = new WiredEffectToggleFurni(1, 1, base, "", 0, 0);
        toggle.setRoomId(7);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getConfig).thenReturn(config);

            // Official layout: [toggle type], the furni source in the official source slot.
            WiredSettings settings = new WiredSettings(new int[] {0}, "", new int[] {1234}, -1);
            settings.setFurniSourceTypes(new int[] {WiredSourceUtil.SOURCE_SELECTOR});
            assertTrue(toggle.saveData(settings, null));
        }

        String saved = toggle.getWiredData();
        assertTrue(saved.contains("1234"), saved);
        assertTrue(saved.contains("\"furniSource\":" + WiredSourceUtil.SOURCE_SELECTOR), saved);
    }
}
