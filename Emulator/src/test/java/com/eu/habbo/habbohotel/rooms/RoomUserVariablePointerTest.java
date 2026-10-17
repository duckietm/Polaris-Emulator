package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraPointerVariable;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableTargets;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** A user pointer is listed among the user variables, but nothing can be stored under it. */
class RoomUserVariablePointerTest {
    private static final int POINTER = 9;
    private static final int USER_ID = 7;

    private static WiredExtraPointerVariable pointer(int sourceType) throws Exception {
        WiredExtraPointerVariable pointer = new WiredExtraPointerVariable(POINTER, 1, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data"))
                .thenReturn("{\"variableName\":\"ptr\",\"sourceType\":" + sourceType + ",\"accessorVariableId\":5}");
        pointer.loadWiredData(set, null);
        return pointer;
    }

    private static RoomUserVariableManager manager(InteractionWiredExtra... boxes) {
        Room room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        Set<InteractionWiredExtra> extras = new LinkedHashSet<>(List.of(boxes));
        when(room.getId()).thenReturn(44);
        when(room.getCurrentHabbos()).thenReturn(new ConcurrentHashMap<>());
        when(room.getRoomSpecialTypes()).thenReturn(special);
        when(special.getExtras()).thenReturn(extras);
        when(special.getExtras(anyInt(), anyInt())).thenReturn(new LinkedHashSet<>());
        for (InteractionWiredExtra box : boxes)
            when(special.getExtra(box.getId())).thenReturn(box);
        return new RoomUserVariableManager(room, new RoomUserVariableRepository(mock(DataSource.class)), () -> 1000);
    }

    @Test
    void aUserPointerIsListedAsAVariableThatNeedsAStack() throws Exception {
        RoomUserVariableManager manager = manager(pointer(WiredVariableTargets.OFFICIAL_USER));

        WiredVariableDefinitionInfo info = manager.getDefinitionInfo(POINTER);
        assertNotNull(info);
        assertTrue(info.requiresExecutionContext());
        assertEquals(1, manager.getAllDefinitionInfos().size());
        assertEquals("ptr", manager.createSnapshot().getDefinitions().get(0).getName());
    }

    @Test
    void aPointerOfAnotherSourceTypeIsNotAUserVariable() throws Exception {
        RoomUserVariableManager manager = manager(pointer(WiredVariableTargets.OFFICIAL_FURNI));

        assertNull(manager.getDefinitionInfo(POINTER));
        assertTrue(manager.getAllDefinitionInfos().isEmpty());
    }

    @Test
    void nothingIsStoredUnderAPointer() throws Exception {
        RoomUserVariableManager manager = manager(pointer(WiredVariableTargets.OFFICIAL_USER));

        assertFalse(manager.assignVariable(USER_ID, POINTER, 5, true));
        assertFalse(manager.updateVariableValue(USER_ID, POINTER, 5));
        assertFalse(manager.removeVariable(USER_ID, POINTER));
        assertFalse(manager.hasVariable(USER_ID, POINTER));
    }
}
