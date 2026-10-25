package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.navigation.DisplayMode;
import com.eu.habbo.habbohotel.navigation.ListMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/**
 * users_navigator_settings had its only unique key on user_id, so every category after the first failed to insert
 * and its preference was lost; and any name the client sent became a connection, an insert and a map entry.
 */
class NavigatorWindowSettingsTest {

    private static MockedStatic<Emulator> database(Connection connection) throws Exception {
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        when(database.getDataSource().getConnection()).thenReturn(connection);
        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getDatabase).thenReturn(database);
        return emulator;
    }

    @Test
    void aCategoryIsStoredAsItsOwnRowOrUpdated() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));

        try (MockedStatic<Emulator> ignored = database(connection)) {
            new HabboNavigatorWindowSettings(7).setListMode("official", ListMode.THUMBNAILS);
        }

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue()
                .contains("ON DUPLICATE KEY UPDATE list_type = VALUES(list_type), display = VALUES(display)"));
    }

    @Test
    void anOversizedNameIsIgnoredAndTheNumberOfCategoriesIsCapped() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));
        HabboNavigatorWindowSettings settings = new HabboNavigatorWindowSettings(7);

        try (MockedStatic<Emulator> ignored = database(connection)) {
            settings.setDisplayMode(
                    "x".repeat(HabboNavigatorWindowSettings.MAX_CATEGORY_LENGTH + 1), DisplayMode.COLLAPSED);
            assertTrue(settings.displayModes.isEmpty());
            verify(connection, never()).prepareStatement(anyString());

            for (int i = 0; i < HabboNavigatorWindowSettings.MAX_CATEGORIES + 20; i++) {
                settings.setDisplayMode("category" + i, DisplayMode.COLLAPSED);
            }
        }

        assertEquals(HabboNavigatorWindowSettings.MAX_CATEGORIES, settings.displayModes.size());
        assertFalse(HabboNavigatorWindowSettings.acceptsCategory(" "));
    }

    @Test
    void theMigrationMovesTheKeyToUserAndCategory() throws Exception {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V20261025203416__navigator_settings_per_category.sql"));

        assertTrue(migration.contains("'ALTER TABLE `users_navigator_settings` DROP INDEX `userid`'"));
        assertTrue(migration.contains("IF(@old_key = 'user_id',"), "only the known one-row-per-user key is dropped");
        assertTrue(migration.contains(
                "ADD UNIQUE KEY IF NOT EXISTS `uq_users_navigator_settings_caption` (`user_id`, `caption`)"));
    }
}
