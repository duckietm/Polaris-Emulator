package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Types;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoomUserVariablePersistenceCharacterizationTest {

    @Test
    void permanentAssignmentsKeepTheirSqlAndParameterOrder() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        try (RoomJdbcTestSupport.InstalledDatabase ignored = RoomJdbcTestSupport.install(dataSource)) {
            Room room = mock(Room.class);
            when(room.getId()).thenReturn(44);
            RoomUserVariableManager manager = new RoomUserVariableManager(room);

            Class<?> assignmentType =
                    Class.forName(RoomUserVariableManager.class.getName() + "$VariableAssignment");
            Constructor<?> constructor = assignmentType.getDeclaredConstructor(Integer.class, int.class, int.class);
            constructor.setAccessible(true);
            Object assignment = constructor.newInstance(null, 101, 202);

            Method upsert = RoomUserVariableManager.class.getDeclaredMethod(
                    "upsertPersistentAssignment", int.class, int.class, assignmentType);
            upsert.setAccessible(true);
            upsert.invoke(manager, 7, 9, assignment);

            RoomJdbcTestSupport.SqlCall call = dataSource.calls().getFirst();
            assertEquals(
                    "INSERT INTO room_user_wired_variables (room_id, user_id, variable_item_id, value, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value), updated_at = VALUES(updated_at)",
                    call.sql());
            assertEquals(
                    Map.of(1, 44, 2, 7, 3, 9, 4, Types.INTEGER, 5, 101, 6, 202),
                    call.parameters());
            assertEquals("update", call.operation());
        }
    }
}
