package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import polaris.Emulator;
import polaris.plugin.Event;
import polaris.plugin.PluginManager;
import polaris.plugin.events.rooms.RoomUnloadedEvent;
import polaris.plugin.events.rooms.RoomUnloadingEvent;

class RoomLifecycleCharacterizationTest {

    private PluginManager originalPluginManager;
    private RecordingPluginManager pluginManager;

    @BeforeEach
    void installPluginManager() throws Exception {
        Field field = Emulator.class.getDeclaredField("pluginManager");
        field.setAccessible(true);
        this.originalPluginManager = (PluginManager) field.get(null);
        this.pluginManager = new RecordingPluginManager();
        field.set(null, this.pluginManager);
    }

    @AfterEach
    void restorePluginManager() throws Exception {
        Field field = Emulator.class.getDeclaredField("pluginManager");
        field.setAccessible(true);
        field.set(null, this.originalPluginManager);
    }

    @Test
    void emptyRoomUnloadsOnTheSixtyFirstCycleAndOccupancyResetsTheCounter() {
        RoomCycleManager cycles = new RoomCycleManager(new Room(41, 7));

        for (int cycle = 1; cycle <= 60; cycle++) {
            assertFalse(cycles.advanceIdleUnload(true), "cycle " + cycle);
        }
        assertTrue(cycles.advanceIdleUnload(true), "cycle 61");

        assertFalse(cycles.advanceIdleUnload(false));
        for (int cycle = 1; cycle <= 60; cycle++) {
            assertFalse(cycles.advanceIdleUnload(true), "reset cycle " + cycle);
        }
        assertTrue(cycles.advanceIdleUnload(true), "reset cycle 61");
    }

    @Test
    void unloadingEventPrecedesUnloadedEvent() {
        Room room = new Room(41, 7);

        room.dispose();

        assertEquals(List.of(RoomUnloadingEvent.class, RoomUnloadedEvent.class), this.pluginManager.events);
    }

    @Test
    void cancelledUnloadingEventPreventsTheUnloadedEvent() {
        this.pluginManager.cancelUnloading = true;
        Room room = new Room(41, 7);

        room.dispose();

        assertEquals(List.of(RoomUnloadingEvent.class), this.pluginManager.events);
    }

    @Test
    void preventUnloadingSuppressesBothLifecycleEvents() {
        Room room = new Room(41, 7);
        room.preventUnloading = true;

        room.dispose();

        assertTrue(this.pluginManager.events.isEmpty());
    }

    @Test
    void disposeRemainsSynchronizedForPublicMonitorCompatibility() throws Exception {
        assertTrue(Modifier.isSynchronized(Room.class.getMethod("dispose").getModifiers()));
    }

    private static final class RecordingPluginManager extends PluginManager {
        private final List<Class<?>> events = new ArrayList<>();
        private boolean cancelUnloading;

        @Override
        public <T extends Event> T fireEvent(T event) {
            this.events.add(event.getClass());
            if (this.cancelUnloading && event instanceof RoomUnloadingEvent) {
                event.setCancelled(true);
            }
            return event;
        }
    }
}
