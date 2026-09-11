package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.TestDatabase;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class WiredArrayMigrationIT {

    @Test
    void createsArrayStorageAndCatalogEntriesUsingInstalledItemIds() throws Exception {
        assumeTrue(TestDatabase.dockerAvailable(), "Docker is required for the MariaDB migration test");

        try (HikariDataSource dataSource = TestDatabase.freshDatabase("wired_arrays")) {
            MigrationRunner.migrate(dataSource);

            assertTrue(tableExists(dataSource, "room_wired_array_values"));
            assertTrue(tableExists(dataSource, "room_wired_array_entries"));
            assertEquals(3, integer(dataSource, """
                    SELECT COUNT(*) FROM wired_emulator_settings
                    WHERE `key` IN (
                        'hotel.wired.arrays.max_entries',
                        'hotel.wired.arrays.max_populated_cells_per_owner',
                        'hotel.wired.arrays.max_owners_per_execution'
                    )
                    """));
            assertEquals(3, integer(dataSource, """
                    SELECT COUNT(*) FROM items_base
                    WHERE (item_name = 'wf_act_modify_array' AND sprite_id = 2000029849)
                       OR (item_name = 'wf_cnd_check_array' AND sprite_id = 2000029850)
                       OR (item_name = 'wf_xtra_array_capture_variable' AND sprite_id = 2000029851)
                    """));
            assertEquals(3, integer(dataSource, """
                    SELECT COUNT(*)
                    FROM catalog_items catalog
                    JOIN items_base base ON catalog.item_ids = CAST(base.id AS CHAR)
                    JOIN catalog_pages page ON page.id = catalog.page_id
                    WHERE (base.item_name = 'wf_act_modify_array' AND page.caption_save = 'effects')
                       OR (base.item_name = 'wf_cnd_check_array' AND page.caption_save = 'conditions')
                       OR (base.item_name = 'wf_xtra_array_capture_variable'
                           AND LOWER(REPLACE(REPLACE(page.caption_save, '-', ''), ' ', '')) = 'addons')
                    """));
        }
    }

    private static boolean tableExists(HikariDataSource dataSource, String table) throws Exception {
        try (Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        SELECT COUNT(*)
                        FROM information_schema.TABLES
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                        """)) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1;
            }
        }
    }

    private static int integer(HikariDataSource dataSource, String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getInt(1) : Integer.MIN_VALUE;
        }
    }
}
