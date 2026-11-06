package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.WiredPlatform;
import polaris.core.ConfigurationManager;
import polaris.hotel.GameEnvironment;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.messages.incoming.wired.WiredSaveException;

/**
 * Every furni-selecting effect refuses a selection above {@code hotel.wired.furni.selection.count};
 * this one read the ids straight in. The cap bounds what one firing can move, and the client's
 * picker limit is not something the server can rely on.
 */
class WiredEffectMoveFurniAsGroupTest {

    @Test
    void refusesMoreFurniThanTheHotelAllows() {
        WiredEffectMoveFurniAsGroup box = new WiredEffectMoveFurniAsGroup(1, 1, mock(Item.class), "", 0, 0);
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt("hotel.wired.furni.selection.count")).thenReturn(2);
        RoomManager rooms = mock(RoomManager.class);
        when(rooms.getRoom(anyInt())).thenReturn(mock(Room.class));
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getRoomManager()).thenReturn(rooms);

        try (MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class)) {
            platform.when(WiredPlatform::configuration).thenReturn(config);
            platform.when(WiredPlatform::gameEnvironment).thenReturn(environment);

            WiredSaveException refused = assertThrows(
                    WiredSaveException.class,
                    () -> box.saveData(
                            new WiredSettings(
                                    new int[] {0, WiredSourceUtil.SOURCE_SELECTED}, "", new int[] {1, 2, 3}, 0),
                            null));

            assertEquals("Too many furni selected", refused.getMessage());
        }
    }
}
