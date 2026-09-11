package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayFieldDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayMutationResult;
import com.eu.habbo.habbohotel.wired.arrays.WiredArraySettings;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayStructuralOperation;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableType;
import com.eu.habbo.habbohotel.wired.arrays.WiredVariableDefinitionData;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class RoomArrayVariableLifecycleTest {
    @Test
    void userDepartureDropsTemporaryArraysWithoutDeletingPermanentStorage() {
        var database = new RoomJdbcTestSupport.RecordingDataSource();
        Room room = room(44);
        var manager = manager(room, database);
        var variable = variable(44, 91, WiredArrayVariableType.USER, false, schema(2));
        assertTrue(manager.give(variable, 501, false).changed());
        assertTrue(manager.mutate(variable, 501, WiredArrayStructuralOperation.APPEND, 0, 0, Map.of(1, 7L, 2, 8L))
                .changed());

        manager.clearAssignmentsForUser(501);

        assertFalse(manager.hasValue(variable, 501));
        assertTrue(database.calls().isEmpty());
    }

    @Test
    void cachedReferencesBecomeUnreadableWhenSourceIsRevoked() {
        var database = new RoomJdbcTestSupport.RecordingDataSource();
        database.rows(sql -> List.of(Map.of("logical_length", 0, "version", 1L)));
        var manager = manager(room(45), database);
        var variable = variable(44, 91, WiredArrayVariableType.USER, true, schema(2));
        when(variable.isArrayShared()).thenReturn(true);
        assertTrue(manager.hasValue(variable, 501));

        when(variable.isArraySourceValid()).thenReturn(false);

        assertFalse(manager.hasValue(variable, 501));
        assertNull(manager.getValue(variable, 501));
    }

    @Test
    void sourceRemovalInvalidatesReferenceCachesAfterAnInFlightLoad() throws Exception {
        var database = new RoomJdbcTestSupport.RecordingDataSource();
        AtomicBoolean removed = new AtomicBoolean();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch finishLoad = new CountDownLatch(1);
        database.rows(sql -> {
            if (!sql.contains("LEFT JOIN") || removed.get()) return List.of();
            loading.countDown();
            try {
                if (!finishLoad.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for load");
            } catch (InterruptedException exception) {
                throw new AssertionError(exception);
            }
            return List.of(Map.of("logical_length", 0, "version", 1L));
        });
        database.beforeExecution(sql -> {
            if (sql.startsWith("DELETE")) removed.set(true);
        });
        Room source = room(44);
        Room reference = room(45);
        GameEnvironment environment = mock(GameEnvironment.class);
        RoomManager rooms = mock(RoomManager.class);
        when(environment.getRoomManager()).thenReturn(rooms);
        when(rooms.getActiveRooms()).thenReturn(new java.util.ArrayList<>(List.of(source, reference)));
        when(source.gameEnvironment()).thenReturn(environment);
        when(reference.gameEnvironment()).thenReturn(environment);
        var sourceManager = manager(source, database);
        var referenceManager = manager(reference, database);
        var variable = variable(44, 91, WiredArrayVariableType.USER, true, schema(2));
        try (var workers = Executors.newFixedThreadPool(2)) {
            var read = workers.submit(() -> referenceManager.hasValue(variable, 501));
            assertTrue(loading.await(5, TimeUnit.SECONDS));
            var deletion = workers.submit(() -> sourceManager.removeDefinition(91));
            finishLoad.countDown();
            assertTrue(read.get(5, TimeUnit.SECONDS));
            deletion.get(5, TimeUnit.SECONDS);
            assertFalse(referenceManager.hasValue(variable, 501));
        }
    }

    @Test
    void fieldGrowthRejectsUnloadedPersistedOwnersBeforeApplyingDefinition() {
        var database = new RoomJdbcTestSupport.RecordingDataSource();
        database.rows(sql -> sql.contains("maximum_entries") ? List.of(Map.of("maximum_entries", 2048)) : List.of());
        var manager = manager(room(44), database);
        var variable = variable(44, 91, WiredArrayVariableType.USER, true, schema(2));
        AtomicBoolean applied = new AtomicBoolean();

        assertThrows(
                IllegalArgumentException.class,
                () -> manager.updateDefinition(variable, schema(3), true, () -> applied.set(true)));

        assertFalse(applied.get());
        assertEquals(2, variable.getArrayDefinition().getFields().size());
    }

    @Test
    void fieldGrowthRejectsCachedTemporaryOwnersBeforeApplyingDefinition() {
        int previousLimit = WiredArraySettings.maxPopulatedCellsPerOwner();
        int previousEntries = WiredArraySettings.maxEntries();
        int previousOwners = WiredArraySettings.maxOwnersPerExecution();
        long previousInterval = WiredArraySettings.metricsLogIntervalMs();
        try {
            WiredArraySettings.configure(2048, 4, 50);
            var manager = manager(room(44), new RoomJdbcTestSupport.RecordingDataSource());
            var variable = variable(44, 91, WiredArrayVariableType.USER, false, schema(2));
            assertTrue(manager.give(variable, 501, false).changed());
            assertTrue(manager.mutate(variable, 501, WiredArrayStructuralOperation.APPEND, 0, 0, Map.of(1, 7L, 2, 8L))
                    .changed());
            assertTrue(manager.mutate(variable, 501, WiredArrayStructuralOperation.APPEND, 0, 0, Map.of(1, 9L, 2, 10L))
                    .changed());
            AtomicBoolean applied = new AtomicBoolean();

            assertThrows(
                    IllegalArgumentException.class,
                    () -> manager.updateDefinition(variable, schema(3), false, () -> applied.set(true)));

            assertFalse(applied.get());
            assertEquals(7L, manager.getValue(variable, 501).readField(0, 1));
            assertEquals(2, manager.getValue(variable, 501).getOccupiedCount());
        } finally {
            WiredArraySettings.configure(previousEntries, previousLimit, previousOwners, previousInterval);
        }
    }

    @Test
    void permanentUserDepartureEvictsOnlyCacheAndReloadsStoredContents() {
        var database = new RoomJdbcTestSupport.RecordingDataSource();
        database.rows(sql -> List.of(
                Map.of("logical_length", 1, "version", 1L, "entry_index", 0, "entry_data", "{\"1\":7,\"2\":8}")));
        var manager = manager(room(44), database);
        var variable = variable(44, 91, WiredArrayVariableType.USER, true, schema(2));
        assertEquals(7L, manager.getValue(variable, 501).readField(0, 1));

        manager.clearAssignmentsForUser(501);

        assertEquals(7L, manager.getValue(variable, 501).readField(0, 1));
        assertTrue(database.calls().stream().allMatch(call -> call.sql().startsWith("SELECT")));
    }

    @Test
    void emptyAssignmentsCannotBypassAggregateOwnerQuota() {
        var database = new RoomJdbcTestSupport.RecordingDataSource();
        var manager = manager(room(44), database);
        for (int id = 1; id <= WiredArraySettings.maxArraysPerOwner(); id++) {
            assertTrue(manager.give(variable(44, id, WiredArrayVariableType.USER, false, schema(2)), 501, false)
                    .changed());
        }
        assertEquals(
                WiredArrayMutationResult.POPULATED_CELL_LIMIT,
                manager.give(variable(44, 999, WiredArrayVariableType.USER, false, schema(2)), 501, false)
                        .result());
        assertFalse(manager.hasValue(variable(44, 999, WiredArrayVariableType.USER, false, schema(2)), 501));
    }

    @Test
    void failedBatchPersistenceDoesNotPublishAnyCandidate() {
        var database = new RoomJdbcTestSupport.RecordingDataSource();
        database.rows(sql -> List.of(Map.of("logical_length", 0, "version", 1L)));
        var manager = manager(room(44), database);
        var variable = variable(44, 91, WiredArrayVariableType.USER, true, schema(2));
        assertTrue(manager.hasValue(variable, 501));
        assertTrue(manager.hasValue(variable, 502));
        database.failUpdates(true);

        var outcomes = manager.mutateBatch(
                variable,
                List.of(
                        new RoomArrayVariableManager.StructuralMutation(
                                501, WiredArrayStructuralOperation.APPEND, 0, 0, Map.of(1, 7L, 2, 8L)),
                        new RoomArrayVariableManager.StructuralMutation(
                                502, WiredArrayStructuralOperation.APPEND, 0, 0, Map.of(1, 9L, 2, 10L))));

        assertTrue(
                outcomes.stream().allMatch(outcome -> outcome.result() == WiredArrayMutationResult.PERSISTENCE_FAILED));
        assertEquals(0, manager.getValue(variable, 501).getOccupiedCount());
        assertEquals(0, manager.getValue(variable, 502).getOccupiedCount());
    }

    @Test
    void leasedEntriesSurviveOtherRoomUpdatesMovesAndCacheRelease() {
        var database = new RoomJdbcTestSupport.RecordingDataSource();
        var version = new java.util.concurrent.atomic.AtomicLong(1);
        database.rows(sql -> {
            if (sql.contains("FOR UPDATE")) return List.of(Map.of("version", version.get()));
            if (sql.contains("owner_sizes")) return List.of();
            return List.of(
                    Map.of("logical_length", 2, "version", 1L, "entry_index", 0, "entry_data", "{\"1\":7,\"2\":8}"),
                    Map.of("logical_length", 2, "version", 1L, "entry_index", 1, "entry_data", "{\"1\":9,\"2\":10}"));
        });
        database.beforeExecution(sql -> {
            if (sql.startsWith("UPDATE room_wired_array_values")) version.incrementAndGet();
        });
        Room first = room(44);
        Room second = room(45);
        GameEnvironment environment = mock(GameEnvironment.class);
        RoomManager rooms = mock(RoomManager.class);
        when(environment.getRoomManager()).thenReturn(rooms);
        when(rooms.getActiveRooms()).thenReturn(new java.util.ArrayList<>(List.of(first, second)));
        when(first.gameEnvironment()).thenReturn(environment);
        when(second.gameEnvironment()).thenReturn(environment);
        var firstManager = manager(first, database);
        var secondManager = manager(second, database);
        var variable = variable(44, 91, WiredArrayVariableType.USER, true, schema(2));
        when(variable.isArrayShared()).thenReturn(true);
        Object lease = firstManager.retainCapturedValue(variable, 501);
        long identity = firstManager.getValue(variable, 501).getEntry(0).getRuntimeId();

        assertTrue(secondManager
                .mutateField(
                        variable,
                        501,
                        0,
                        1,
                        com.eu.habbo.habbohotel.wired.arrays.WiredArrayNumericOperation.ASSIGN,
                        15L)
                .changed());
        assertTrue(secondManager
                .mutate(variable, 501, WiredArrayStructuralOperation.MOVE, 0, 1, Map.of())
                .changed());
        firstManager.clearCache();
        secondManager.clearCache();

        assertEquals(1, firstManager.getValue(variable, 501).findEntryIndex(identity));
        assertEquals(15L, firstManager.getValue(variable, 501).readField(1, 1));
        assertTrue(secondManager
                .mutate(variable, 501, WiredArrayStructuralOperation.SET_ENTRY, 1, 0, Map.of(1, 99L, 2, 100L))
                .changed());
        assertEquals(-1, firstManager.getValue(variable, 501).findEntryIndex(identity));
        assertTrue(secondManager.remove(variable, 501));
        assertFalse(firstManager.hasValue(variable, 501));
        java.lang.ref.Reference.reachabilityFence(lease);
    }

    private static Room room(int id) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        return room;
    }

    private static RoomArrayVariableManager manager(Room room, RoomJdbcTestSupport.RecordingDataSource database) {
        var manager = new RoomArrayVariableManager(room, new RoomArrayVariableRepository(database), () -> 123);
        when(room.getArrayVariableManager()).thenReturn(manager);
        return manager;
    }

    private static WiredArrayVariableDefinition variable(
            int storageRoom, int id, WiredArrayVariableType type, boolean permanent, WiredArrayDefinition schema) {
        var definition = mock(WiredArrayVariableDefinition.class);
        when(definition.getId()).thenReturn(id);
        when(definition.isArray()).thenReturn(true);
        when(definition.isArrayDeclared()).thenReturn(true);
        when(definition.isArrayPermanent()).thenReturn(permanent);
        when(definition.isArrayWritable()).thenReturn(true);
        when(definition.isArraySourceValid()).thenReturn(true);
        when(definition.getArrayStorageRoomId(anyInt())).thenReturn(storageRoom);
        when(definition.getArrayStorageDefinitionItemId()).thenReturn(id);
        when(definition.getArrayVariableType()).thenReturn(type);
        when(definition.getArrayDefinition()).thenReturn(schema);
        return definition;
    }

    private static WiredArrayDefinition schema(int fields) {
        var data = new WiredVariableDefinitionData();
        data.valueShape = "array";
        data.arrayFormat = "record";
        data.arrayMode = "list";
        data.maxEntries = 2048;
        data.nextFieldId = fields + 1;
        data.fields = java.util.stream.IntStream.rangeClosed(1, fields)
                .mapToObj(id -> new WiredArrayFieldDefinition(id, "Field" + id, id - 1))
                .toList();
        return WiredArrayDefinition.fromData(data, 2048);
    }
}
