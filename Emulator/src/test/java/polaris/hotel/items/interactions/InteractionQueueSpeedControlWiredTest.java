package polaris.hotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.threading.ThreadPooling;

class InteractionQueueSpeedControlWiredTest {
    @Test
    void toggleStateShowsTheNextGroupButTheRollersKeepTheChosenSpeed() {
        InteractionQueueSpeedControl control = new InteractionQueueSpeedControl(1, 1, mock(Item.class), "0", 0, 0);
        Room room = mock(Room.class);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getThreading).thenReturn(mock(ThreadPooling.class));

            for (int i = 0; i < 4; i++) {
                control.wiredStepMode(room, true);
                assertEquals(0, control.getRollerModeState());
            }

            control.wiredStepMode(room, true);
            assertEquals("3", control.getExtradata());
            assertEquals(0, control.getRollerModeState());

            // A user's click changes the speed to the group it shows next.
            control.stepMode(room, true);
            assertEquals("6", control.getExtradata());
            assertEquals(6, control.getRollerModeState());
        }
    }
}
