package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RoomItemManagerWiredLoadIsolationTest {

    @Test
    void malformedWiredRowIsQuarantinedWithoutBlockingLaterItems() throws Exception {
        Room room = new Room(41, 7);
        room.replaceSpecialTypes(new RoomSpecialTypes());
        RoomItemManager manager = room.getItemManager();
        InteractionWiredTrigger malformed = trigger(1001, WiredTriggerType.SAY_SOMETHING);
        InteractionWiredTrigger healthy = trigger(1002, WiredTriggerType.ENTER_ROOM);
        manager.getRoomItems().put(malformed.getId(), malformed);
        manager.getRoomItems().put(healthy.getId(), healthy);
        room.getRoomSpecialTypes().addTrigger(malformed);
        room.getRoomSpecialTypes().addTrigger(healthy);

        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, true, false);
        when(rows.getInt("id")).thenReturn(1001, 1002);
        doThrow(new IllegalArgumentException("malformed fixture"))
                .when(malformed)
                .loadWiredData(rows, room);

        manager.loadWiredData(connection);

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(query.capture());
        assertTrue(
                query.getValue().contains("wired_data<>''"),
                "The production loader must continue excluding never-configured blank rows");
        verify(malformed).loadWiredData(rows, room);
        verify(healthy).loadWiredData(rows, room);
        assertFalse(room.getRoomSpecialTypes()
                .getTriggers(WiredTriggerType.SAY_SOMETHING)
                .contains(malformed));
        assertSame(malformed, manager.getRoomItems().get(1001), "quarantine must not delete hotel furniture");
    }

    private static InteractionWiredTrigger trigger(int id, WiredTriggerType type) {
        InteractionWiredTrigger trigger = mock(InteractionWiredTrigger.class);
        when(trigger.getId()).thenReturn(id);
        when(trigger.getType()).thenReturn(type);
        return trigger;
    }
}
