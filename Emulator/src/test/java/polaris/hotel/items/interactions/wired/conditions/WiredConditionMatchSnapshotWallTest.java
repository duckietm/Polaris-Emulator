package polaris.hotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.hotel.GameEnvironment;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredServices;
import polaris.hotel.wired.core.WiredState;

/** The snapshot conditions take wall furni: state, wall position and altitude, and no direction. */
class WiredConditionMatchSnapshotWallTest {

    @Test
    void aWallItemMatchesOnItsWallPositionAndIgnoresDirection() {
        Room room = mock(Room.class);
        UserItem torch = mock(UserItem.class);
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.WALL);
        when(torch.getBaseItem()).thenReturn(base);
        when(torch.getId()).thenReturn(55);
        when(torch.getExtradata()).thenReturn("1");
        AtomicReference<String> wall = new AtomicReference<>(":w=2,3 l=10,20 r");
        when(torch.getWallPosition()).thenAnswer(invocation -> wall.get());
        when(torch.getRotation()).thenReturn(0);
        when(room.getUserItem(55)).thenReturn(torch);
        GameEnvironment environment = mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(anyInt())).thenReturn(room);
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt(anyString())).thenReturn(20);
        when(config.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));

        WiredConditionMatchStatePosition condition =
                new WiredConditionMatchStatePosition(1, 1, mock(Item.class), "", 0, 0);
        WiredContext ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getConfig).thenReturn(config);
            // state, direction, position, altitude, picked furni
            condition.saveData(new WiredSettings(new int[] {1, 1, 1, 0}, "", new int[] {55}, 0));

            when(torch.getRotation()).thenReturn(4); // a wall item has no direction
            assertTrue(condition.evaluate(ctx));

            wall.set(":w=2,4 l=10,20 r");
            assertFalse(condition.evaluate(ctx));
        }
    }
}
