package polaris.hotel.items.interactions;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Random;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.core.WiredManager;
import polaris.threading.ThreadPooling;

/** "State changed" fires for the state changes outside the plain toggle, as in Habbo (here: a random-state furni). */
class InteractionStateChangedTriggerTest {

    @Test
    void aRandomStateClickFiresStateChanged() throws Exception {
        Item base = mock(Item.class);
        when(base.getCustomParams()).thenReturn("states=4,delay=500");
        InteractionRandomState furni = new InteractionRandomState(1, 1, base, "", 0, 0);
        Room room = mock(Room.class);
        GameClient client = mock(GameClient.class);
        User user = mock(User.class);
        RoomUnit unit = mock(RoomUnit.class);
        when(client.getUser()).thenReturn(user);
        when(user.getRoomUnit()).thenReturn(unit);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedStatic<WiredManager> wired = mockStatic(WiredManager.class)) {
            emulator.when(Emulator::getRandom).thenReturn(new Random(1));
            emulator.when(Emulator::getThreading).thenReturn(mock(ThreadPooling.class));

            furni.onRandomStateClick(client, room);

            wired.verify(() -> WiredManager.triggerFurniStateChanged(room, unit, furni));
            wired.verify(() -> WiredManager.triggerFurniStateUpdated(eq(room), eq(unit), eq(furni), eq(false)));
        }
    }
}
