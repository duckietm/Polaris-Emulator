package polaris.hotel.users;

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

    private static User user(THashMap<String, Object> cache, String look) throws Exception {
        UserStats stats = mock(UserStats.class);
        Field field = UserStats.class.getDeclaredField("cache");
        field.setAccessible(true);
        field.set(stats, cache);
        UserInfo info = mock(UserInfo.class);
        when(info.getLook()).thenReturn(look);
        User user = mock(User.class);
        when(user.getUserStats()).thenReturn(stats);
        when(user.getUserInfo()).thenReturn(info);
        return user;
    }

    @Test
    void theOwnLookIsKeptOnceAndGivenBack() throws Exception {
        THashMap<String, Object> cache = new THashMap<>();
        User user = user(cache, "hd-180-1.ch-210-66");

        RoomLookOverride.apply(user, "hd-180-1.ch-255-82");
        RoomLookOverride.apply(user, "hd-180-1.ch-3030-64");
        assertEquals(
                "hd-180-1.ch-210-66",
                cache.get(RoomLookOverride.ORIGINAL_LOOK_KEY),
                "a second box keeps the first own look");

        assertTrue(RoomLookOverride.restore(user));
        verify(user.getUserInfo()).setLook("hd-180-1.ch-210-66");
        assertFalse(cache.containsKey(RoomLookOverride.ORIGINAL_LOOK_KEY));
    }

    @Test
    void aUserNeverDressedByARoomKeepsTheirLook() throws Exception {
        User user = user(new THashMap<>(), "hd-1");

        assertFalse(RoomLookOverride.restore(user));
        verify(user.getUserInfo(), never()).setLook(anyString());
    }

    @Test
    void theBoxUsesItAndItIsUndoneOnExitAndDisconnect() throws Exception {
        String box = Files.readString(
                Path.of("src/main/java/polaris/hotel/items/interactions/wired/effects/WiredEffectGiveLook.java"));
        String plugins = Files.readString(Path.of("src/main/java/polaris/plugin/PluginManager.java"));

        assertTrue(box.contains("RoomLookOverride.apply(user, validated);"));
        assertFalse(box.contains("getUserInfo().setLook(validated)"), "the box no longer sets the look for good");
        assertTrue(box.contains("ClothingValidationManager.validateLook(user, this.figure, gender)"));
        assertTrue(plugins.contains("RoomLookOverride.class.getMethod(\"onUserExitRoomEvent\""));
        assertTrue(plugins.contains("RoomLookOverride.class.getMethod(\"onUserDisconnectEvent\""));
    }
}
