package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        RoomUserVariableRepository.StoredAssignment assignment =
                repository.findByUser(44, 7).getFirst();

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
                "DELETE FROM room_user_wired_variables " + "WHERE room_id = ? AND user_id = ? AND variable_item_id = ?",
                call.sql());
        assertEquals(Map.of(1, 44, 2, 7, 3, 91), call.parameters());
    }

    @Test
    void pagesSavedUsersWithAnIndexedOrderAndOnlyTheRowsAPageNeeds() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        Map<String, Object> row = new HashMap<>();
        row.put("user_id", 7);
        row.put("value", 5);
        row.put("created_at", 101);
        row.put("updated_at", 202);
        row.put("username", "dave");
        dataSource.rows(ignored -> List.of(row));
        RoomUserVariableRepository repository = new RoomUserVariableRepository(dataSource);

        List<RoomUserVariableRepository.SavedUser> rows = repository.pageUsers(
                44, 91, RoomUserVariableStore.Order.UPDATE_TIME, true, null, List.of(3, 4), 20, 30);

        assertEquals(List.of(new RoomUserVariableRepository.SavedUser(7, "dave", 5, 101, 202)), rows);
        RoomJdbcTestSupport.SqlCall call = dataSource.calls().getFirst();
        assertEquals(
                "SELECT saved.user_id, saved.value, saved.created_at, saved.updated_at, users.username "
                        + "FROM room_user_wired_variables saved LEFT JOIN users ON users.id = saved.user_id "
                        + "WHERE saved.room_id = ? AND saved.variable_item_id = ? AND saved.user_id > 0 "
                        + "AND saved.user_id NOT IN (?, ?) "
                        + "ORDER BY saved.updated_at DESC, saved.user_id DESC LIMIT ? OFFSET ?",
                call.sql());
        assertEquals(Map.of(1, 44, 2, 91, 3, 3, 4, 4, 5, 30, 6, 20), call.parameters());
    }

    @Test
    void aDailyCounterSortsByTheValueItReadsAsToday() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        RoomUserVariableRepository repository = new RoomUserVariableRepository(dataSource);

        repository.pageUsers(44, 91, RoomUserVariableStore.Order.VALUE, false, 86_400, List.of(), 0, 50);

        RoomJdbcTestSupport.SqlCall call = dataSource.calls().getFirst();
        assertEquals(
                "SELECT saved.user_id, saved.value, saved.created_at, saved.updated_at, users.username "
                        + "FROM room_user_wired_variables saved LEFT JOIN users ON users.id = saved.user_id "
                        + "WHERE saved.room_id = ? AND saved.variable_item_id = ? AND saved.user_id > 0 "
                        + "ORDER BY CASE WHEN saved.value IS NULL OR saved.value = 0 OR saved.updated_at >= ? "
                        + "THEN saved.value ELSE 0 END ASC, saved.user_id ASC LIMIT ? OFFSET ?",
                call.sql());
        assertEquals(Map.of(1, 44, 2, 91, 3, 86_400, 4, 50, 5, 0), call.parameters());
        assertTrue(RoomUserVariableRepository.pageUsersSql(RoomUserVariableStore.Order.ID, false, false, 0)
                .endsWith("ORDER BY saved.user_id ASC LIMIT ? OFFSET ?"));
        assertTrue(RoomUserVariableRepository.pageUsersSql(RoomUserVariableStore.Order.VALUE, true, false, 0)
                .contains("ORDER BY saved.value DESC, saved.user_id DESC"));
    }

    @Test
    void savedRowsOfOneUserStayInTheirRoom() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        RoomUserVariableRepository repository = new RoomUserVariableRepository(dataSource);

        repository.updateValue(44, 7, 91, 12, 300);
        repository.hasUser(44, 7);
        repository.countUsers(44, 91, List.of(7));
        repository.participantIdByName(44, "dave");

        List<RoomJdbcTestSupport.SqlCall> calls = dataSource.calls();
        assertEquals(
                "UPDATE room_user_wired_variables SET value = ?, updated_at = ? "
                        + "WHERE room_id = ? AND user_id = ? AND variable_item_id = ?",
                calls.get(0).sql());
        assertEquals(Map.of(1, 12, 2, 300, 3, 44, 4, 7, 5, 91), calls.get(0).parameters());
        assertEquals(
                "SELECT 1 FROM room_user_wired_variables WHERE room_id = ? AND user_id = ? LIMIT 1",
                calls.get(1).sql());
        assertEquals(Map.of(1, 44, 2, 7), calls.get(1).parameters());
        assertEquals(
                "SELECT COUNT(*) FROM room_user_wired_variables saved WHERE saved.room_id = ? "
                        + "AND saved.variable_item_id = ? AND saved.user_id > 0 AND saved.user_id NOT IN (?)",
                calls.get(2).sql());
        assertEquals(Map.of(1, 44, 2, 91, 3, 7), calls.get(2).parameters());
        assertTrue(calls.get(3).sql().contains("saved.room_id = ? AND saved.user_id = users.id"));
        assertEquals(Map.of(1, "dave", 2, 44), calls.get(3).parameters());
    }
}
