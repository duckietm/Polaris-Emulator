package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.modtool.WordFilter;
import com.eu.habbo.habbohotel.navigation.EventCategory;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.navigator.NewNavigatorEventCategoriesComposer;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Set Room Ad creates the room promotion the catalogue charges credits and points for,
 * and its title is shown across the hotel. So it is gated like the other boxes that
 * create value, its text goes through the word filter, and its category has to be one
 * the navigator actually offers.
 */
class WiredEffectSetRoomAdTest {

    private static final int VISIBLE_CATEGORY = 7;
    private static final int HIDDEN_CATEGORY = 8;

    private final List<EventCategory> previousCategories = NewNavigatorEventCategoriesComposer.CATEGORIES;

    @AfterEach
    void restoreCategories() {
        NewNavigatorEventCategoriesComposer.CATEGORIES = this.previousCategories;
    }

    private static WiredEffectSetRoomAd box() {
        return new WiredEffectSetRoomAd(1, 1, mock(Item.class), "", 0, 0);
    }

    private static WiredSettings settings(int category, String captionAndDescription) {
        return new WiredSettings(new int[] {category}, captionAndDescription, new int[0], 0);
    }

    private static ConfigurationManager config() {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean("hotel.wired.reward.require_permission", true)).thenReturn(true);
        when(config.getInt(anyString(), anyInt())).thenAnswer(i -> i.getArgument(1));
        return config;
    }

    private static GameClient superWired() {
        Habbo habbo = mock(Habbo.class);
        when(habbo.hasPermission(Permission.ACC_SUPERWIRED)).thenReturn(true);
        GameClient client = mock(GameClient.class);
        when(client.getHabbo()).thenReturn(habbo);
        return client;
    }

    @Test
    void savingWithoutTheRewardPermissionIsRefused() throws Exception {
        // Built before the stubbing: Mockito reads a mock made inside a thenReturn(...)
        // argument as an unfinished stubbing.
        ConfigurationManager config = config();

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);

            assertFalse(box().saveData(settings(0, "Party\tcome in"), null));
        }
    }

    @Test
    void aCategoryTheNavigatorDoesNotOfferFallsBackToTheDefault() throws Exception {
        NewNavigatorEventCategoriesComposer.CATEGORIES = List.of(
                new EventCategory(VISIBLE_CATEGORY, "Parties", true),
                new EventCategory(HIDDEN_CATEGORY, "Staff", false));

        WiredEffectSetRoomAd offered = box();
        WiredEffectSetRoomAd hidden = box();
        WiredEffectSetRoomAd unknown = box();
        ConfigurationManager config = config();
        GameClient client = superWired();

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            platform.when(WiredPlatform::gameEnvironment).thenReturn(null);

            assertTrue(offered.saveData(settings(VISIBLE_CATEGORY, "Party\tcome in"), client));
            assertTrue(hidden.saveData(settings(HIDDEN_CATEGORY, "Party\tcome in"), client));
            assertTrue(unknown.saveData(settings(42, "Party\tcome in"), client));
        }

        assertEquals(
                VISIBLE_CATEGORY,
                WiredEffectTestFixtures.json(offered).get("category").getAsInt());
        assertEquals(
                0,
                WiredEffectTestFixtures.json(hidden).get("category").getAsInt(),
                "a category the navigator hides is not one a room ad may use");
        assertEquals(
                0,
                WiredEffectTestFixtures.json(unknown).get("category").getAsInt(),
                "a category that exists nowhere is not one a room ad may use");
    }

    @Test
    void theTextGoesThroughTheWordFilter() throws Exception {
        NewNavigatorEventCategoriesComposer.CATEGORIES = List.of();

        WordFilter filter = mock(WordFilter.class);
        when(filter.filter(anyString(), any()))
                .thenAnswer(i -> i.<String>getArgument(0).replace("spam", "bobba"));
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getWordFilter()).thenReturn(filter);

        WiredEffectSetRoomAd box = box();
        ConfigurationManager config = config();
        GameClient client = superWired();

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class)) {
            emulator.when(Emulator::getConfig).thenReturn(config);
            platform.when(WiredPlatform::gameEnvironment).thenReturn(environment);

            assertTrue(box.saveData(settings(0, "spam title\tspam body"), client));
        }

        assertEquals(
                "bobba title", WiredEffectTestFixtures.json(box).get("caption").getAsString());
        assertEquals(
                "bobba body",
                WiredEffectTestFixtures.json(box).get("description").getAsString());
    }
}
