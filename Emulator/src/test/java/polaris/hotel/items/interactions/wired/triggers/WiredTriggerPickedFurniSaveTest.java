package polaris.hotel.items.interactions.wired.triggers;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomManager;
import polaris.hotel.users.UserItem;

/**
 * The official editor has no furni source on a trigger: picked furni mean the picks. A trigger that
 * gets the "trigger" source (0) next to picks keeps them instead of dropping them.
 */
class WiredTriggerPickedFurniSaveTest {

    @Test
    void walkOnFurniKeepsItsPicksWhenTheSourceSaysTrigger() throws Exception {
        Room room = WiredTriggerTestSupport.room(7);
        UserItem rug = mock(UserItem.class);
        when(rug.getId()).thenReturn(1234);
        when(room.getUserItem(1234)).thenReturn(rug);

        GameEnvironment environment = mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS);
        RoomManager rooms = mock(RoomManager.class);
        when(environment.getRoomManager()).thenReturn(rooms);
        when(rooms.getRoom(7)).thenReturn(room);

        WiredTriggerUserWalkOnFurni trigger =
                new WiredTriggerUserWalkOnFurni(1, 1, WiredTriggerTestSupport.boxBase(), "", 0, 0);
        trigger.setRoomId(7);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);

            assertTrue(trigger.saveData(new WiredSettings(new int[] {0}, "", new int[] {1234}, -1)));
        }

        String saved = trigger.getWiredData();
        assertTrue(saved.contains("1234"), saved);
        assertTrue(saved.contains("100"), saved);

        // Walking onto the picked rug fires it; walking onto another furni does not.
        UserItem chair = mock(UserItem.class);
        when(chair.getId()).thenReturn(99);
        when(room.getItemsAt(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(java.util.Set.of());
        assertTrue(trigger.matches(trigger, walkOn(room, rug)));
        org.junit.jupiter.api.Assertions.assertFalse(trigger.matches(trigger, walkOn(room, chair)));
    }

    private static polaris.hotel.wired.core.WiredEvent walkOn(Room room, UserItem item) {
        return polaris.hotel.wired.core.WiredEvent.builder(polaris.hotel.wired.core.WiredEvent.Type.USER_WALKS_ON, room)
                .sourceItem(item)
                .build();
    }
}
