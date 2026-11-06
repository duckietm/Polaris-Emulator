package polaris.hotel.items.interactions.wired.effects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.TextsManager;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.threading.ThreadPooling;
import polaris.threading.runnables.RoomUnitKick;

/** Kick User on the room owner whispers the exception in the Wired bubble (34), as in Habbo. */
class WiredEffectKickUserExceptionTest {

    @Test
    void theOwnerExceptionIsWhisperedInTheWiredBubble() {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        User owner = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(7);
        when(owner.getUserInfo()).thenReturn(info);
        when(room.getUser(unit)).thenReturn(owner);
        when(room.getOwnerId()).thenReturn(7);
        TextsManager texts = mock(TextsManager.class);
        when(texts.getValue(anyString())).thenReturn("Wired kick exception: Room owner");

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getTexts).thenReturn(texts);
            new WiredEffectKickUser(1, 1, base(), "", 0, 0).execute(context(room, unit));
        }

        verify(owner).whisper("Wired kick exception: Room owner", RoomChatMessageBubbles.WIRED);
    }

    /** The whisper used the client unchecked: a user whose connection was closing threw and was never kicked. */
    @Test
    void aUserWithoutAClientIsStillKicked() {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        User leaving = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(8);
        when(leaving.getUserInfo()).thenReturn(info);
        when(leaving.getClient()).thenReturn(null);
        when(room.getUser(unit)).thenReturn(leaving);
        when(room.getOwnerId()).thenReturn(7);
        ThreadPooling threading = mock(ThreadPooling.class);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getThreading).thenReturn(threading);
            new WiredEffectKickUser(1, 1, base(), "", 0, 0).execute(context(room, unit));
        }

        verify(room).giveEffect(leaving, 4, 2);
        verify(threading).run(any(RoomUnitKick.class), eq(2000L));
    }
}
