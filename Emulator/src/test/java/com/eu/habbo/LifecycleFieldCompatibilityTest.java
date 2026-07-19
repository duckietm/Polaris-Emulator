package com.eu.habbo;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleFieldCompatibilityTest {

    @Test
    void lifecycleFieldsRemainPublicStaticAndDirectlyWritable() throws Exception {
        boolean ready = Emulator.isReady;
        boolean shuttingDown = Emulator.isShuttingDown;
        boolean stopped = Emulator.stopped;
        try {
            Emulator.isReady = !ready;
            Emulator.isShuttingDown = !shuttingDown;
            Emulator.stopped = !stopped;

            assertEquals(!ready, Emulator.isReady);
            assertEquals(!shuttingDown, Emulator.isShuttingDown);
            assertEquals(!stopped, Emulator.stopped);
        } finally {
            Emulator.isReady = ready;
            Emulator.isShuttingDown = shuttingDown;
            Emulator.stopped = stopped;
        }

        assertPublicStaticMutableBoolean("isReady");
        assertPublicStaticMutableBoolean("isShuttingDown");
        assertPublicStaticMutableBoolean("stopped");
    }

    @Test
    void roomVisibilityFieldsKeepTheirTypesAndEncapsulation() throws Exception {
        assertPrivateMutableField("layout", RoomLayout.class);
        assertPrivateMutableField("roomSpecialTypes", RoomSpecialTypes.class);
    }

    @Test
    void crossThreadControlFieldsAreVolatile() throws Exception {
        assertVolatile(Emulator.class.getDeclaredField("isReady"));
        assertVolatile(Emulator.class.getDeclaredField("isShuttingDown"));
        assertVolatile(Emulator.class.getDeclaredField("stopped"));
        assertVolatile(Room.class.getDeclaredField("layout"));
        assertVolatile(Room.class.getDeclaredField("roomSpecialTypes"));
    }

    private static void assertPublicStaticMutableBoolean(String name) throws Exception {
        Field field = Emulator.class.getDeclaredField(name);
        int modifiers = field.getModifiers();

        assertEquals(boolean.class, field.getType());
        assertTrue(Modifier.isPublic(modifiers));
        assertTrue(Modifier.isStatic(modifiers));
        assertFalse(Modifier.isFinal(modifiers));
    }

    private static void assertPrivateMutableField(String name, Class<?> type) throws Exception {
        Field field = Room.class.getDeclaredField(name);
        int modifiers = field.getModifiers();

        assertEquals(type, field.getType());
        assertTrue(Modifier.isPrivate(modifiers));
        assertFalse(Modifier.isStatic(modifiers));
        assertFalse(Modifier.isFinal(modifiers));
    }

    private static void assertVolatile(Field field) {
        assertTrue(Modifier.isVolatile(field.getModifiers()),
                () -> field.getDeclaringClass().getSimpleName() + "." + field.getName()
                        + " must be volatile");
    }
}
