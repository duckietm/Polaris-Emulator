package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableReference;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredVariableReferenceSupport;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A shared user variable is read by other rooms from a cache: clearing every holder must empty it too. */
class RoomUserVariableSharedClearAllTest {
    private static final int ROOM_ID = 44;
    private static final int DEFINITION_ID = 10;

    @Test
    void clearingEveryHolderOfASharedVariableAlsoDropsTheValuesOtherRoomsRead() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource database = new RoomJdbcTestSupport.RecordingDataSource();

        try (RoomJdbcTestSupport.InstalledDatabase ignored = RoomJdbcTestSupport.install(database)) {
            RoomUserVariableManager manager = manager(database);
            WiredExtraVariableReference reference = mock(WiredExtraVariableReference.class);
            when(reference.isUserReference()).thenReturn(true);
            when(reference.getSourceRoomId()).thenReturn(ROOM_ID);
            when(reference.getSourceVariableItemId()).thenReturn(DEFINITION_ID);

            assertTrue(manager.assignVariable(7, DEFINITION_ID, 5, true));
            assertNotNull(WiredVariableReferenceSupport.getSharedUserAssignment(reference, 7));

            assertEquals(1, manager.clearAllAssignments(DEFINITION_ID));

            assertNull(WiredVariableReferenceSupport.getSharedUserAssignment(reference, 7));
        } finally {
            WiredVariableReferenceSupport.clearSharedUserDefinition(ROOM_ID, DEFINITION_ID);
        }
    }

    private static RoomUserVariableManager manager(RoomJdbcTestSupport.RecordingDataSource database) throws Exception {
        WiredExtraUserVariable definition = new WiredExtraUserVariable(DEFINITION_ID, 1, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data"))
                .thenReturn("{\"variableName\":\"points\",\"hasValue\":true,\"availability\":"
                        + WiredExtraUserVariable.AVAILABILITY_SHARED + "}");
        definition.loadWiredData(set, null);

        Room room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        Set<InteractionWiredExtra> extras = new LinkedHashSet<>(List.of(definition));
        when(room.getId()).thenReturn(ROOM_ID);
        when(room.getWiredTimezone()).thenReturn("UTC");
        when(room.getRoomSpecialTypes()).thenReturn(special);
        when(special.getExtras()).thenReturn(extras);
        when(special.getExtras(0, 0)).thenReturn(extras);
        when(special.getExtra(DEFINITION_ID)).thenReturn(definition);
        when(room.getArrayVariableManager()).thenReturn(mock(RoomArrayVariableManager.class));
        return new RoomUserVariableManager(room, new RoomUserVariableRepository(database), () -> 1000);
    }
}
