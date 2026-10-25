package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gnu.trove.map.hash.THashMap;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Give Look set the visitor's look for good: it was saved with the profile, so a trap room replaced the
 * outfit of everyone who walked in. The look now lasts while the user stays in the room.
 */
class RoomLookOverrideTest {

    private static Habbo habbo(THashMap<String, Object> cache, String look) throws Exception {
        HabboStats stats = mock(HabboStats.class);
        Field field = HabboStats.class.getDeclaredField("cache");
        field.setAccessible(true);
        field.set(stats, cache);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getLook()).thenReturn(look);
        Habbo habbo = mock(Habbo.class);
        when(habbo.getHabboStats()).thenReturn(stats);
        when(habbo.getHabboInfo()).thenReturn(info);
        return habbo;
    }

    @Test
    void theOwnLookIsKeptOnceAndGivenBack() throws Exception {
        THashMap<String, Object> cache = new THashMap<>();
        Habbo habbo = habbo(cache, "hd-180-1.ch-210-66");

        RoomLookOverride.apply(habbo, "hd-180-1.ch-255-82");
        RoomLookOverride.apply(habbo, "hd-180-1.ch-3030-64");
        assertEquals(
                "hd-180-1.ch-210-66",
                cache.get(RoomLookOverride.ORIGINAL_LOOK_KEY),
                "a second box keeps the first own look");

        assertTrue(RoomLookOverride.restore(habbo));
        verify(habbo.getHabboInfo()).setLook("hd-180-1.ch-210-66");
        assertFalse(cache.containsKey(RoomLookOverride.ORIGINAL_LOOK_KEY));
    }

    @Test
    void aUserNeverDressedByARoomKeepsTheirLook() throws Exception {
        Habbo habbo = habbo(new THashMap<>(), "hd-1");

        assertFalse(RoomLookOverride.restore(habbo));
        verify(habbo.getHabboInfo(), never()).setLook(anyString());
    }

    @Test
    void theBoxUsesItAndItIsUndoneOnExitAndDisconnect() throws Exception {
        String box = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/items/interactions/wired/effects/WiredEffectGiveLook.java"));
        String plugins = Files.readString(Path.of("src/main/java/com/eu/habbo/plugin/PluginManager.java"));

        assertTrue(box.contains("RoomLookOverride.apply(habbo, validated);"));
        assertFalse(box.contains("getHabboInfo().setLook(validated)"), "the box no longer sets the look for good");
        assertTrue(box.contains("ClothingValidationManager.validateLook(habbo, this.figure, gender)"));
        assertTrue(plugins.contains("RoomLookOverride.class.getMethod(\"onUserExitRoomEvent\""));
        assertTrue(plugins.contains("RoomLookOverride.class.getMethod(\"onUserDisconnectEvent\""));
    }
}
