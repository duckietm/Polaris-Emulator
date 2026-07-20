package com.eu.habbo;

import com.eu.habbo.habbohotel.rooms.Room;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleFieldVisibilityCompatibilityTest {

    @Test
    void publicLifecycleFlagsRemainFieldsAndPublishAcrossThreads()
            throws Exception {
        assertVolatilePublicField(Emulator.class, "isReady");
        assertVolatilePublicField(Emulator.class, "isShuttingDown");
        assertVolatilePublicField(Emulator.class, "stopped");
    }

    @Test
    void roomLifecycleReferencesPublishAcrossThreads() throws Exception {
        assertVolatileField(Room.class, "layout");
        assertVolatileField(Room.class, "roomSpecialTypes");
    }

    private static void assertVolatilePublicField(
            Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        assertTrue(Modifier.isPublic(field.getModifiers()));
        assertTrue(Modifier.isVolatile(field.getModifiers()));
    }

    private static void assertVolatileField(
            Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        assertTrue(Modifier.isVolatile(field.getModifiers()));
    }
}
