package com.eu.habbo.networking.gameserver.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableWebApi;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The keys of a box in a room that is not loaded, read straight from the database. */
class HotelWiredApiStoredKeysIT {
    private static final int OWNER = 940001;
    private static final int ROOM = 940011;
    private static final int BOX = 940201;

    @Test
    void aBoxWhoseBaseItemSaysDefaultIsFoundByItsName() throws Exception {
        requireDocker();
        try (HikariDataSource dataSource = TestDatabase.freshDatabase("wired_api_stored_keys")) {
            MigrationRunner.migrate(dataSource);
            String readKey = WiredExtraVariableWebApi.mintKey();
            String writeKey = WiredExtraVariableWebApi.mintKey();
            try (Connection connection = dataSource.getConnection()) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(
                            "INSERT INTO items_base (id, sprite_id, public_name, item_name, interaction_type)"
                                    + " VALUES (940101, 940101, 'api', 'wf_xtra_var_web_api', 'default')");
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO items (id, user_id, room_id, item_id, wired_data, limited_data) VALUES (?, ?, ?, 940101, ?, '0:0')")) {
                    insert.setInt(1, BOX);
                    insert.setInt(2, OWNER);
                    insert.setInt(3, ROOM);
                    insert.setString(
                            4,
                            "{\"itemId\":" + BOX + ",\"ownerId\":" + OWNER + ",\"readKey\":\"" + readKey
                                    + "\",\"writeKey\":\"" + writeKey + "\",\"bulkDelete\":false}");
                    insert.executeUpdate();
                }
            }

            List<WiredExtraVariableWebApi.KeyState> keys = HotelWiredApiRooms.readStoredKeys(dataSource, ROOM);
            assertEquals(1, keys.size());
            assertEquals(
                    WiredExtraVariableWebApi.Access.READ,
                    keys.get(0).authenticate(WiredExtraVariableWebApi.hashKey(readKey)));
            assertEquals(
                    WiredExtraVariableWebApi.Access.WRITE,
                    keys.get(0).authenticate(WiredExtraVariableWebApi.hashKey(writeKey)));
            assertTrue(HotelWiredApiRooms.readStoredKeys(dataSource, ROOM + 1).isEmpty());
        }
    }

    private static void requireDocker() {
        if (!TestDatabase.dockerAvailable()) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                throw new AssertionError("Docker/Testcontainers is required in CI");
            }
            assumeTrue(false, "Docker/Testcontainers not available - skipping DB integration test");
        }
    }
}
