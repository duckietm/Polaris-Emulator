package polaris.hotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomSpecialTypes;
import polaris.messages.incoming.wired.WiredSaveException;

/** An echo that picks another echo is refused with the official key. */
class WiredExtraVariableEchoSelfReferenceTest {

    @Test
    void anEchoOfAnEchoIsRefused() {
        Room room = mock(Room.class, Answers.RETURNS_DEEP_STUBS);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        WiredExtraVariableEcho other = mock(WiredExtraVariableEcho.class);
        when(specialTypes.getExtra(77)).thenReturn(other);
        GameEnvironment environment = mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(anyInt())).thenReturn(room);
        WiredExtraVariableEcho echo = new WiredExtraVariableEcho(1, 1, mock(Item.class), "", 0, 0);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);

            WiredSaveException refused = assertThrows(
                    WiredSaveException.class,
                    () -> echo.saveData(
                            new WiredSettings(
                                    new int[0],
                                    "{\"sourceTargetType\":0,\"sourceVariableToken\":\"custom:77\",\"sourceVariableItemId\":77}",
                                    new int[0],
                                    0),
                            null));
            assertEquals("wiredfurni.error.echo.self_reference", refused.getMessage());
        }
    }
}
