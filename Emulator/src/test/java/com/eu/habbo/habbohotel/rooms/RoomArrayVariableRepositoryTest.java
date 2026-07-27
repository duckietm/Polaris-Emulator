package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayFieldDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayMutationResult;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayStructuralOperation;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayValue;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableType;
import com.eu.habbo.habbohotel.wired.arrays.WiredVariableDefinitionData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoomArrayVariableRepositoryTest {

    @Test
    void temporaryArraysRequireGiveAndDisappearOnRemove() {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(44);
        WiredArrayVariableDefinition variable = mock(WiredArrayVariableDefinition.class);
        when(variable.getId()).thenReturn(91);
        when(variable.isArray()).thenReturn(true);
        when(variable.isArrayPermanent()).thenReturn(false);
        when(variable.isArrayWritable()).thenReturn(true);
        when(variable.isArraySourceValid()).thenReturn(true);
        when(variable.getArrayStorageRoomId(44)).thenReturn(44);
        when(variable.getArrayStorageDefinitionItemId()).thenReturn(91);
        when(variable.getArrayVariableType()).thenReturn(WiredArrayVariableType.ROOM);
        when(variable.getArrayDefinition()).thenReturn(definition());
        RoomArrayVariableManager manager =
                new RoomArrayVariableManager(room, new RoomArrayVariableRepository(dataSource), () -> 123);

        assertEquals(
                WiredArrayMutationResult.MISSING_OWNER,
                manager.mutate(variable, 44, WiredArrayStructuralOperation.APPEND, 0, 0, Map.of(1, 1L, 2, 2L))
                        .result());
        assertTrue(manager.give(variable, 44, false).changed());
        assertTrue(manager.hasValue(variable, 44));
        assertTrue(manager.remove(variable, 44));
        assertFalse(manager.hasValue(variable, 44));
        assertTrue(dataSource.calls().isEmpty());
    }

    @Test
    void referenceUsesPhysicalSourceRoomAndDefinitionKeys() {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(44);
        WiredArrayVariableDefinition variable = mock(WiredArrayVariableDefinition.class);
        when(variable.getId()).thenReturn(91);
        when(variable.isArray()).thenReturn(true);
        when(variable.isArrayPermanent()).thenReturn(true);
        when(variable.getArrayVariableType()).thenReturn(WiredArrayVariableType.USER);
        when(variable.getArrayDefinition()).thenReturn(definition());
        when(variable.getArrayStorageRoomId(44)).thenReturn(77);
        when(variable.getArrayStorageDefinitionItemId()).thenReturn(99);
        RoomArrayVariableManager manager =
                new RoomArrayVariableManager(room, new RoomArrayVariableRepository(dataSource), () -> 123);

        assertFalse(manager.hasValue(variable, 501));

        assertEquals(
                Map.of(1, 77, 2, 99, 3, 2, 4, 501),
                dataSource.calls().getFirst().parameters());
    }

    @Test
    void readOnlyReferenceRejectsMutationsBeforeStorageAccess() {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(44);
        WiredArrayVariableDefinition variable = mock(WiredArrayVariableDefinition.class);
        when(variable.isArray()).thenReturn(true);
        when(variable.isArrayWritable()).thenReturn(false);
        when(variable.isArraySourceValid()).thenReturn(true);
        when(variable.getArrayVariableType()).thenReturn(WiredArrayVariableType.USER);
        when(variable.getArrayDefinition()).thenReturn(definition());
        when(variable.getArrayStorageRoomId(44)).thenReturn(77);
        when(variable.getArrayStorageDefinitionItemId()).thenReturn(99);
        RoomArrayVariableManager manager =
                new RoomArrayVariableManager(room, new RoomArrayVariableRepository(dataSource), () -> 123);

        assertFalse(manager.give(variable, 501, true).changed());
        assertTrue(dataSource.calls().isEmpty());
    }

    @Test
    void loadsOneHeaderAndItsOrderedEntryMaps() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        Map<String, Object> first = new HashMap<>();
        first.put("logical_length", 2);
        first.put("version", 7L);
        first.put("entry_index", 0);
        first.put("entry_data", "{\"1\":10,\"2\":20}");
        Map<String, Object> second = new HashMap<>();
        second.put("logical_length", 2);
        second.put("version", 7L);
        second.put("entry_index", 1);
        second.put("entry_data", "{\"1\":30,\"2\":40}");
        dataSource.rows(ignored -> List.of(first, second));
        RoomArrayVariableRepository repository = new RoomArrayVariableRepository(dataSource);
        RoomArrayVariableManager.Key key = new RoomArrayVariableManager.Key(44, 91, 2, 7);

        RoomArrayVariableRepository.StoredValue stored = repository.load(key);

        assertEquals(2, stored.logicalLength());
        assertEquals(7L, stored.version());
        assertEquals(Map.of(1, 10L, 2, 20L), stored.entries().get(0));
        assertEquals(Map.of(1, 30L, 2, 40L), stored.entries().get(1));
        assertEquals(
                Map.of(1, 44, 2, 91, 3, 2, 4, 7), dataSource.calls().getFirst().parameters());
    }

    @Test
    void replacementLocksVersionThenWritesHeaderAndEntries() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        dataSource.rows(sql -> sql.contains("FOR UPDATE") ? List.of(Map.of("version", 4L)) : List.of());
        RoomArrayVariableRepository repository = new RoomArrayVariableRepository(dataSource);
        RoomArrayVariableManager.Key key = new RoomArrayVariableManager.Key(44, 91, 1, 44);
        WiredArrayValue value = WiredArrayValue.empty(definition(), 16);
        value.apply(WiredArrayStructuralOperation.APPEND, 0, 0, Map.of(1, 10L, 2, 20L));

        long nextVersion = repository.replace(key, 4L, WiredArrayPersistenceDelta.between(null, value), 123);

        assertEquals(5L, nextVersion);
        assertEquals(4, dataSource.calls().size());
        assertTrue(dataSource.calls().get(0).sql().startsWith("INSERT INTO room_wired_array_values"));
        assertTrue(dataSource.calls().get(1).sql().contains("FOR UPDATE"));
        assertTrue(dataSource.calls().get(2).sql().startsWith("UPDATE room_wired_array_values"));
        assertEquals("batch", dataSource.calls().get(3).operation());
        assertEquals(
                Map.of(1, 44, 2, 91, 3, 1, 4, 44, 5, 0, 6, "{\"1\":10,\"2\":20}"),
                dataSource.calls().get(3).parameters());
    }

    @Test
    void appendPersistsOnlyTheNewEntry() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        dataSource.rows(sql -> sql.contains("FOR UPDATE") ? List.of(Map.of("version", 4L)) : List.of());
        RoomArrayVariableRepository repository = new RoomArrayVariableRepository(dataSource);
        RoomArrayVariableManager.Key key = new RoomArrayVariableManager.Key(44, 91, 1, 44);
        WiredArrayValue before = WiredArrayValue.empty(definition(), 16);
        before.apply(WiredArrayStructuralOperation.APPEND, 0, 0, Map.of(1, 10L, 2, 20L));
        WiredArrayValue after = before.copy();
        after.apply(WiredArrayStructuralOperation.APPEND, 0, 0, Map.of(1, 30L, 2, 40L));

        WiredArrayPersistenceDelta delta = WiredArrayPersistenceDelta.between(before, after);
        long nextVersion = repository.replace(key, 4L, delta, 123);

        assertEquals(5L, nextVersion);
        assertTrue(delta.removedIndexes().isEmpty());
        assertEquals(1, delta.upsertedEntries().size());
        assertEquals(1, delta.upsertedEntries().keySet().iterator().next());
        assertEquals(4, dataSource.calls().size());
        assertEquals("batch", dataSource.calls().getLast().operation());
        assertEquals(1, dataSource.calls().getLast().parameters().get(5));
    }

    @Test
    void ownerCleanupCannotBroadenPastRoomTypeAndOwner() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        RoomArrayVariableRepository repository = new RoomArrayVariableRepository(dataSource);

        repository.deleteOwner(44, 0, 777);

        assertEquals(2, dataSource.calls().size());
        for (RoomJdbcTestSupport.SqlCall call : dataSource.calls()) {
            assertEquals(Map.of(1, 44, 2, 0, 3, 777), call.parameters());
        }
    }

    @Test
    void switchingAnEmptyArrayToTemporaryStorageDeletesPersistedHeaders() {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(44);
        var variable = mock(com.eu.habbo.habbohotel.wired.arrays.WiredArrayVariableDefinition.class);
        when(variable.getId()).thenReturn(91);
        when(variable.isArray()).thenReturn(true);
        when(variable.isArrayPermanent()).thenReturn(false);
        when(variable.getArrayDefinition()).thenReturn(definition());
        RoomArrayVariableManager manager =
                new RoomArrayVariableManager(room, new RoomArrayVariableRepository(dataSource), () -> 123);

        manager.handleDefinitionUpdated(variable);

        assertEquals(2, dataSource.calls().size());
        for (RoomJdbcTestSupport.SqlCall call : dataSource.calls()) {
            assertEquals(Map.of(1, 44, 2, 91), call.parameters());
        }
    }

    private static WiredArrayDefinition definition() {
        WiredVariableDefinitionData data = new WiredVariableDefinitionData();
        data.valueShape = "array";
        data.arrayFormat = "record";
        data.arrayMode = "list";
        data.maxEntries = 8;
        data.nextFieldId = 3;
        data.schemaVersion = 1;
        data.fields =
                List.of(new WiredArrayFieldDefinition(1, "ItemID", 0), new WiredArrayFieldDefinition(2, "Quantity", 1));
        return WiredArrayDefinition.fromData(data, 8);
    }
}
