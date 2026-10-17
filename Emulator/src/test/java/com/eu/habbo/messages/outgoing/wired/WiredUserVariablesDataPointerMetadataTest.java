package com.eu.habbo.messages.outgoing.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraPointerVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableTargets;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The creator-tools packet flags every named pointer under its source type's variable list. */
class WiredUserVariablesDataPointerMetadataTest {

    private static WiredExtraPointerVariable pointer(int id, String name, int sourceType) throws Exception {
        WiredExtraPointerVariable pointer = new WiredExtraPointerVariable(id, 1, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data"))
                .thenReturn("{\"variableName\":\"" + name + "\",\"sourceType\":" + sourceType
                        + ",\"accessorVariableId\":5}");
        pointer.loadWiredData(set, null);
        return pointer;
    }

    @Test
    void eachNamedPointerIsFlaggedUnderItsSourceType() throws Exception {
        Room room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(special);
        LinkedHashSet<InteractionWiredExtra> extras = new LinkedHashSet<>(List.of(
                pointer(9, "a", WiredVariableTargets.OFFICIAL_USER),
                pointer(10, "b", WiredVariableTargets.OFFICIAL_CONTEXT),
                pointer(11, "", WiredVariableTargets.OFFICIAL_FURNI)));
        when(special.getExtras()).thenReturn(extras);

        List<WiredUserVariablesDataComposer.PointerMetadata> metadata =
                WiredUserVariablesDataComposer.resolvePointers(room);

        assertEquals(2, metadata.size());
        assertEquals(
                "[{\"itemId\":9,\"variableType\":2,\"requiresExecutionContext\":true},"
                        + "{\"itemId\":10,\"variableType\":3,\"requiresExecutionContext\":true}]",
                WiredManager.getGson().toJson(metadata));
        assertTrue(WiredUserVariablesDataComposer.resolvePointers(null).isEmpty());
    }
}
