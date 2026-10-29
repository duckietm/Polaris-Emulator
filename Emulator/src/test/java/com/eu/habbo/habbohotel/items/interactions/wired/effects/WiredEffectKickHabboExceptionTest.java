package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.TextsManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.threading.ThreadPooling;
import com.eu.habbo.threading.runnables.RoomUnitKick;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Kick User on the room owner whispers the exception in the Wired bubble (34), as in Habbo. */
class WiredEffectKickHabboExceptionTest {

    @Test
    void theOwnerExceptionIsWhisperedInTheWiredBubble() {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        Habbo owner = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(7);
        when(owner.getHabboInfo()).thenReturn(info);
        when(room.getHabbo(unit)).thenReturn(owner);
        when(room.getOwnerId()).thenReturn(7);
        TextsManager texts = mock(TextsManager.class);
        when(texts.getValue(anyString())).thenReturn("Wired kick exception: Room owner");

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getTexts).thenReturn(texts);
            new WiredEffectKickHabbo(1, 1, base(), "", 0, 0).execute(context(room, unit));
        }

        verify(owner).whisper("Wired kick exception: Room owner", RoomChatMessageBubbles.WIRED);
    }

    /** The whisper used the client unchecked: a user whose connection was closing threw and was never kicked. */
    @Test
    void aUserWithoutAClientIsStillKicked() {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        Habbo leaving = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(8);
        when(leaving.getHabboInfo()).thenReturn(info);
        when(leaving.getClient()).thenReturn(null);
        when(room.getHabbo(unit)).thenReturn(leaving);
        when(room.getOwnerId()).thenReturn(7);
        ThreadPooling threading = mock(ThreadPooling.class);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getThreading).thenReturn(threading);
            new WiredEffectKickHabbo(1, 1, base(), "", 0, 0).execute(context(room, unit));
        }

        verify(room).giveEffect(leaving, 4, 2);
        verify(threading).run(any(RoomUnitKick.class), eq(2000L));
    }
}
