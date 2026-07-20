package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoomUserVariableRepositoryTest {

    @Test
    void readsAssignmentsWithTheirTimestamps() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        Map<String, Object> row = new HashMap<>();
        row.put("variable_item_id", 91);
        row.put("value", 303);
        row.put("created_at", 101);
        row.put("updated_at", 202);
        dataSource.rows(ignored -> List.of(row));

        RoomUserVariableRepository repository = new RoomUserVariableRepository(dataSource);
        RoomUserVariableRepository.StoredAssignment assignment = repository.findByUser(44, 7).getFirst();

        assertEquals(91, assignment.definitionItemId());
        assertEquals(303, assignment.value());
        assertEquals(101, assignment.createdAt());
        assertEquals(202, assignment.updatedAt());
        assertEquals(Map.of(1, 44, 2, 7), dataSource.calls().getFirst().parameters());
    }

    @Test
    void deletesOneAssignmentWithoutBroadeningItsScope() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        RoomUserVariableRepository repository = new RoomUserVariableRepository(dataSource);

        repository.delete(44, 7, 91);

        RoomJdbcTestSupport.SqlCall call = dataSource.calls().getFirst();
        assertEquals(
                "DELETE FROM room_user_wired_variables "
                        + "WHERE room_id = ? AND user_id = ? AND variable_item_id = ?",
                call.sql());
        assertEquals(Map.of(1, 44, 2, 7, 3, 91), call.parameters());
    }
}
