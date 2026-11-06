package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.core.ConfigurationManager;

/** The hotel switch and allow-list of the achievement and reward-track boxes: off unless turned on. */
class WiredHotelProgressPolicyTest {

    static ConfigurationManager config(String prefix, boolean enabled, String allowed, int max, int window) {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean(anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(config.getValue(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        when(config.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        when(config.getBoolean(prefix + ".enabled", false)).thenReturn(enabled);
        when(config.getValue(prefix + ".allowed", "")).thenReturn(allowed);
        when(config.getInt(prefix + ".max_per_window", WiredHotelProgressPolicy.DEFAULT_MAX_PER_WINDOW))
                .thenReturn(max);
        when(config.getInt(prefix + ".window_seconds", WiredHotelProgressPolicy.DEFAULT_WINDOW_SECONDS))
                .thenReturn(window);
        return config;
    }

    @Test
    void withoutAConfigurationNothingIsAllowed() {
        WiredHotelProgressPolicy policy = WiredHotelProgressPolicy.achievements(null);

        assertFalse(policy.enabled());
        assertFalse(policy.allows("ACH_Game"));
    }

    @Test
    void theSwitchIsOffByDefault() {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean(anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(config.getValue(anyString(), anyString())).thenReturn("ACH_Game");

        assertFalse(WiredHotelProgressPolicy.achievements(config).allows("ACH_Game"));
        assertFalse(WiredHotelProgressPolicy.rewardTracks(config).allows("ACH_Game"));
    }

    @Test
    void onlyListedNamesAreAllowed() {
        WiredHotelProgressPolicy policy = WiredHotelProgressPolicy.achievements(
                config(WiredHotelProgressPolicy.ACHIEVEMENTS, true, " ACH_Game, ACH_Race;ACH_Tag\nACH_Hide ", 10, 60));

        assertTrue(policy.enabled());
        assertEquals(List.of("ACH_Game", "ACH_Race", "ACH_Tag", "ACH_Hide"), List.copyOf(policy.allowed()));
        assertTrue(policy.allows("ACH_Race"));
        assertFalse(policy.allows("ACH_RoomEntry"), "an unlisted hotel achievement stays out of reach");
        assertFalse(policy.allows("ach_race"), "names match exactly");
        assertEquals(10, policy.maxPerWindow());
        assertEquals(60_000L, policy.windowMs());
    }

    @Test
    void anEmptyListOrAZeroCapTurnsItOff() {
        assertFalse(
                WiredHotelProgressPolicy.rewardTracks(config(WiredHotelProgressPolicy.REWARD_TRACKS, true, " ", 10, 60))
                        .enabled());
        assertFalse(WiredHotelProgressPolicy.rewardTracks(
                        config(WiredHotelProgressPolicy.REWARD_TRACKS, true, "season_1", 0, 60))
                .enabled());
    }

    @Test
    void theWindowIsClamped() {
        assertEquals(
                1_000L,
                WiredHotelProgressPolicy.achievements(config(WiredHotelProgressPolicy.ACHIEVEMENTS, true, "A", 1, -5))
                        .windowMs());
        assertEquals(
                WiredHotelProgressPolicy.MAX_WINDOW_SECONDS * 1000L,
                WiredHotelProgressPolicy.achievements(
                                config(WiredHotelProgressPolicy.ACHIEVEMENTS, true, "A", 1, Integer.MAX_VALUE))
                        .windowMs());
    }

    @Test
    void theListIsBounded() {
        String many = "x".repeat(WiredHotelProgressPolicy.MAX_ENTRY_LENGTH + 1) + ",ok";
        assertEquals(List.of("ok"), List.copyOf(WiredHotelProgressPolicy.parseAllowed(many)));

        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < WiredHotelProgressPolicy.MAX_ALLOWED_ENTRIES + 20; i++) {
            huge.append("n").append(i).append(',');
        }
        assertEquals(
                WiredHotelProgressPolicy.MAX_ALLOWED_ENTRIES,
                WiredHotelProgressPolicy.parseAllowed(huge.toString()).size());
    }
}
