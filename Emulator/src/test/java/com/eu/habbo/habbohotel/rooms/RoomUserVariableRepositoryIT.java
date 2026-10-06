package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The web API's saved-row queries for users not in the room, run on the migrated schema. */
class RoomUserVariableRepositoryIT {
    private static final int ROOM = 920001;
    private static final int OTHER_ROOM = 920002;
    private static final int VARIABLE = 77;

    @Test
    void savedRowsOfAbsentUsersPageCountAndWriteOnRealMariaDb() throws Exception {
        requireDocker();
        try (HikariDataSource dataSource = TestDatabase.freshDatabase("room_user_variables_api")) {
            MigrationRunner.migrate(dataSource);
            seedUsers(dataSource);
            RoomUserVariableRepository repository = new RoomUserVariableRepository(dataSource);

            repository.upsert(ROOM, 920101, VARIABLE, 30, 100, 300);
            repository.upsert(ROOM, 920102, VARIABLE, 10, 200, 100);
            repository.upsert(ROOM, 920103, VARIABLE, null, 300, 200);
            repository.upsert(ROOM, -5, VARIABLE, 99, 400, 400); // a pet's row: never a user
            repository.upsert(OTHER_ROOM, 920104, VARIABLE, 50, 500, 500);

            for (RoomUserVariableStore.Order order : RoomUserVariableStore.Order.values()) {
                for (boolean descending : new boolean[] {false, true}) {
                    List<RoomUserVariableRepository.SavedUser> page =
                            repository.pageUsers(ROOM, VARIABLE, order, descending, null, Set.of(), 0, 10);
                    assertEquals(3, page.size(), order + " " + descending);
                    assertTrue(page.stream().allMatch(row -> row.userId() > 0 && row.username() != null));
                }
            }

            List<RoomUserVariableRepository.SavedUser> byValue = repository.pageUsers(
                    ROOM, VARIABLE, RoomUserVariableStore.Order.VALUE, true, null, Set.of(), 0, 10);
            assertEquals(
                    List.of(920101, 920102, 920103),
                    byValue.stream().map(row -> row.userId()).toList());
            assertNull(byValue.get(2).value());
            assertEquals("absent_one", byValue.get(0).username());

            List<RoomUserVariableRepository.SavedUser> excluded = repository.pageUsers(
                    ROOM, VARIABLE, RoomUserVariableStore.Order.ID, false, null, Set.of(920101, 920103), 0, 10);
            assertEquals(
                    List.of(920102), excluded.stream().map(row -> row.userId()).toList());

            List<RoomUserVariableRepository.SavedUser> daily =
                    repository.pageUsers(ROOM, VARIABLE, RoomUserVariableStore.Order.VALUE, false, 250, Set.of(), 1, 1);
            assertEquals(1, daily.size());

            assertEquals(3, repository.countUsers(ROOM, VARIABLE, Set.of()));
            assertEquals(2, repository.countUsers(ROOM, VARIABLE, Set.of(920102)));

            assertTrue(repository.hasUser(ROOM, 920101));
            assertFalse(repository.hasUser(ROOM, 920104));
            assertEquals(920101, repository.participantIdByName(ROOM, "absent_one"));
            assertEquals(0, repository.participantIdByName(ROOM, "absent_four"));
            assertEquals("absent_two", repository.username(920102));

            assertTrue(repository.updateValue(ROOM, 920102, VARIABLE, 42, 600));
            assertFalse(repository.updateValue(ROOM, 920104, VARIABLE, 1, 600));
            assertEquals(42, repository.find(ROOM, 920102, VARIABLE).value());

            assertTrue(repository.hasDefinition(ROOM, VARIABLE));
            repository.deleteDefinition(ROOM, VARIABLE);
            assertFalse(repository.hasDefinition(ROOM, VARIABLE));
            assertTrue(repository.hasDefinition(OTHER_ROOM, VARIABLE));
        }
    }

    private static void seedUsers(HikariDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (id, username, password, ip_register, ip_current)
                    VALUES (920101, 'absent_one', '!', '127.0.0.1', '127.0.0.1'),
                           (920102, 'absent_two', '!', '127.0.0.1', '127.0.0.1'),
                           (920103, 'absent_three', '!', '127.0.0.1', '127.0.0.1'),
                           (920104, 'absent_four', '!', '127.0.0.1', '127.0.0.1')
                    """);
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
