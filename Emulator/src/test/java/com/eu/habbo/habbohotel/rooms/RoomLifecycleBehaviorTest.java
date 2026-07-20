package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.PluginManager;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoomLifecycleBehaviorTest {

    private PluginManager originalPlugins;
    private PluginManager plugins;

    @BeforeEach
    void installPlugins() throws Exception {
        Field field = Emulator.class.getDeclaredField("pluginManager");
        field.setAccessible(true);
        this.originalPlugins = (PluginManager) field.get(null);
        this.plugins = mock(PluginManager.class);
        field.set(null, this.plugins);
    }

    @AfterEach
    void restorePlugins() throws Exception {
        Field field = Emulator.class.getDeclaredField("pluginManager");
        field.setAccessible(true);
        field.set(null, this.originalPlugins);
    }

    @Test
    void cancelledUncacheEventRetainsTheRoomWithoutDisposal() {
        RoomManager manager = new RoomManager(false);
        TrackingRoom room = new TrackingRoom(41, 7);
        manager.registerActiveRoom(room);
        doAnswer(invocation -> {
                    Event event = invocation.getArgument(0);
                    event.setCancelled(true);
                    return event;
                })
                .when(this.plugins)
                .fireEvent(any());

        manager.unloadRoomsForHabbo(owner(7));

        assertSame(room, manager.getRoom(41));
        assertEquals(0, room.disposeCalls);
    }

    @Test
    void acceptedUncacheEventDisposesBeforeRemovingTheRoom() {
        RoomManager manager = new RoomManager(false);
        TrackingRoom room = new TrackingRoom(41, 7);
        manager.registerActiveRoom(room);
        doAnswer(invocation -> invocation.getArgument(0))
                .when(this.plugins)
                .fireEvent(any());

        manager.unloadRoomsForHabbo(owner(7));

        assertEquals(1, room.disposeCalls);
        assertEquals(null, manager.getRoom(41));
    }

    private static Habbo owner(int id) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(id);
        when(habbo.getHabboInfo()).thenReturn(info);
        return habbo;
    }

    private static final class TrackingRoom extends Room {
        private int disposeCalls;

        private TrackingRoom(int id, int ownerId) {
            super(id, ownerId);
        }

        @Override
        public synchronized void dispose() {
            this.disposeCalls++;
        }
    }
}
