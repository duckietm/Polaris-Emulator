package com.eu.habbo.habbohotel.items.interactions;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.threading.ThreadPooling;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** "State changed" fires for the state changes outside the plain toggle, as in Habbo (here: a random-state furni). */
class InteractionStateChangedTriggerTest {

    @Test
    void aRandomStateClickFiresStateChanged() throws Exception {
        Item base = mock(Item.class);
        when(base.getCustomParams()).thenReturn("states=4,delay=500");
        InteractionRandomState furni = new InteractionRandomState(1, 1, base, "", 0, 0);
        Room room = mock(Room.class);
        GameClient client = mock(GameClient.class);
        Habbo habbo = mock(Habbo.class);
        RoomUnit unit = mock(RoomUnit.class);
        when(client.getHabbo()).thenReturn(habbo);
        when(habbo.getRoomUnit()).thenReturn(unit);

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
