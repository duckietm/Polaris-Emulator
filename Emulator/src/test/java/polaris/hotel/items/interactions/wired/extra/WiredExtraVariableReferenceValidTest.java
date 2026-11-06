package polaris.hotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.wired.WiredSaveException;

/** A reference to a variable that is not shared is refused with the official key. */
class WiredExtraVariableReferenceValidTest {

    @Test
    void aReferenceToAnUnsharedVariableIsRefused() {
        Room room = mock(Room.class, Answers.RETURNS_DEEP_STUBS);
        GameEnvironment environment = mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(anyInt())).thenReturn(room);
        WiredExtraVariableReference reference = new WiredExtraVariableReference(1, 1, mock(Item.class), "", 0, 0);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedStatic<WiredVariableNameValidator> names =
                        mockStatic(WiredVariableNameValidator.class, Mockito.CALLS_REAL_METHODS);
                MockedStatic<WiredVariableReferenceSupport> shared = mockStatic(WiredVariableReferenceSupport.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            names.when(() -> WiredVariableNameValidator.validateDefinitionName(any(), anyInt(), any()))
                    .thenAnswer(invocation -> null);
            shared.when(() -> WiredVariableReferenceSupport.findSharedDefinition(any(), anyInt(), anyInt(), anyInt()))
                    .thenReturn(null);

            WiredSaveException refused = assertThrows(
                    WiredSaveException.class,
                    () -> reference.saveData(
                            new WiredSettings(
                                    new int[0],
                                    "{\"variableName\":\"score\",\"sourceRoomId\":5,\"sourceVariableItemId\":9}",
                                    new int[0],
                                    0),
                            null));
            assertEquals("wiredfurni.error.variables.reference.valid", refused.getMessage());
        }
    }
}
