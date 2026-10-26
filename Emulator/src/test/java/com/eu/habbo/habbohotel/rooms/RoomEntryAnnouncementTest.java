package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.users.inventory.EffectsComponent;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUsersComposer;
import com.eu.habbo.plugin.PluginManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/** Observed on AIR: the entering user gets its own Users entry and status once, not again from the broadcast. */
class RoomEntryAnnouncementTest {

    @Test
    void theNewcomerIsAnnouncedToTheOthersAndSeesItselfOnce() throws Exception {
        Habbo entrant = habbo();
        Habbo present = habbo();
        Room room = mock(Room.class);
        Field lock = Room.class.getField("roomUnitLock");
        lock.setAccessible(true);
        lock.set(room, new Object());
        ConcurrentHashMap<Integer, Habbo> current = new ConcurrentHashMap<>(Map.of(1, entrant, 2, present));
        when(room.getCurrentHabbos()).thenReturn(current);
        when(room.getHabbos()).thenReturn(List.of(entrant, present));
        PluginManager plugins = mock(PluginManager.class);
        when(plugins.isRegistered(any(), anyBoolean())).thenReturn(false);

        RoomManager manager = new RoomManager(false);
        Method announce = RoomManager.class.getDeclaredMethod("announceEntry", Habbo.class, Room.class);
        announce.setAccessible(true);
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedConstruction<RoomUsersComposer> users = mockConstruction(RoomUsersComposer.class);
                MockedConstruction<RoomUserStatusComposer> statuses = mockConstruction(RoomUserStatusComposer.class)) {
            emulator.when(Emulator::getPluginManager).thenReturn(plugins);

            announce.invoke(manager, entrant, room);

            assertEquals(2, users.constructed().size(), "one entry for the others, one batch for the newcomer");
            assertEquals(2, statuses.constructed().size());
        }

        verify(entrant.getClient(), never()).sendResponse(any(ServerMessage.class));
        verify(entrant.getClient(), times(2)).sendResponse(any(MessageComposer.class));
        verify(present.getClient(), times(2)).sendResponse((ServerMessage) any());
    }

    private static Habbo habbo() {
        Habbo habbo = mock(Habbo.class);
        when(habbo.getClient()).thenReturn(mock(GameClient.class));
        when(habbo.getRoomUnit()).thenReturn(mock(RoomUnit.class));
        when(habbo.getHabboStats()).thenReturn(mock(HabboStats.class));
        HabboInventory inventory = mock(HabboInventory.class);
        when(inventory.getEffectsComponent()).thenReturn(mock(EffectsComponent.class));
        when(habbo.getInventory()).thenReturn(inventory);
        return habbo;
    }
}
