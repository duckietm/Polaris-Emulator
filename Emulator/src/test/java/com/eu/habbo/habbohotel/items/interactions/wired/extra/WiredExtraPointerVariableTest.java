package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.wired.protocol.WiredAddonCodes;
import com.eu.habbo.habbohotel.wired.protocol.WiredHolders;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableTargets;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

/** The official pointer variable box: AIR's editor layout, validation and persistence. */
class WiredExtraPointerVariableTest {
    private static final int ACCESSOR = 5;

    private static WiredExtraContextVariable contextVariable(int id, boolean hasValue) throws Exception {
        WiredExtraContextVariable variable = new WiredExtraContextVariable(id, 1, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn("{\"variableName\":\"target\",\"hasValue\":" + hasValue + "}");
        variable.loadWiredData(set, null);
        return variable;
    }

    private static Room room(InteractionWiredExtra... boxes) {
        Room room = mock(Room.class, Answers.RETURNS_DEEP_STUBS);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        Set<InteractionWiredExtra> extras = new LinkedHashSet<>(List.of(boxes));
        when(room.getRoomSpecialTypes()).thenReturn(special);
        when(special.getExtras()).thenReturn(extras);
        for (InteractionWiredExtra box : boxes)
            when(special.getExtra(box.getId())).thenReturn(box);
        return room;
    }

    private static WiredSettings settings(Room room, int sourceType, String name, String... variableIds) {
        WiredSettings settings = new WiredSettings(new int[] {sourceType}, name, new int[0], 0);
        settings.setVariableIds(variableIds);
        settings.setRoom(room);
        return settings;
    }

    @Test
    void itSavesAirsLayoutAndWritesItBack() throws Exception {
        WiredExtraPointerVariable pointer = new WiredExtraPointerVariable(9, 1, mock(Item.class), "", 0, 0);
        Room room = room(contextVariable(ACCESSOR, true), pointer);

        assertTrue(pointer.saveData(settings(room, WiredVariableTargets.OFFICIAL_CONTEXT, "my ptr", "5"), null));

        assertEquals("my_ptr", pointer.getVariableName());
        assertEquals(WiredVariableTargets.OFFICIAL_CONTEXT, pointer.getSourceType());
        assertEquals(ACCESSOR, pointer.getAccessorVariableId());
        assertArrayEquals(new String[] {"5"}, pointer.officialVariableIds());

        WiredExtraPointerVariable loaded = new WiredExtraPointerVariable(9, 1, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(pointer.getWiredData());
        loaded.loadWiredData(set, null);
        assertEquals("my_ptr", loaded.getVariableName());
        assertEquals(WiredVariableTargets.OFFICIAL_CONTEXT, loaded.getSourceType());
        assertEquals(ACCESSOR, loaded.getAccessorVariableId());
    }

    @Test
    void anUnknownSourceTypeIsFurniAirsDefault() throws Exception {
        WiredExtraPointerVariable pointer = new WiredExtraPointerVariable(9, 1, mock(Item.class), "", 0, 0);
        Room room = room(contextVariable(ACCESSOR, true), pointer);

        pointer.saveData(settings(room, 7, "ptr", "custom:5"), null);

        assertEquals(WiredVariableTargets.OFFICIAL_FURNI, pointer.getSourceType());
        assertEquals(ACCESSOR, pointer.getAccessorVariableId());
    }

    @Test
    void theAccessorMustBeAContextVariableWithAValue() throws Exception {
        WiredExtraPointerVariable pointer = new WiredExtraPointerVariable(9, 1, mock(Item.class), "", 0, 0);
        WiredExtraPointerVariable other = new WiredExtraPointerVariable(10, 1, mock(Item.class), "", 0, 0);
        Room room = room(contextVariable(ACCESSOR, false), pointer, other);

        assertEquals(
                "wiredfurni.params.variables.validation.missing_variable",
                assertThrows(WiredSaveException.class, () -> pointer.saveData(settings(room, 0, "ptr"), null))
                        .getMessage());
        assertEquals(
                "wiredfurni.params.variables.validation.invalid_variable",
                assertThrows(WiredSaveException.class, () -> pointer.saveData(settings(room, 0, "ptr", "5"), null))
                        .getMessage());
        assertEquals(
                "wiredfurni.error.variables.requires_execution_context",
                assertThrows(WiredSaveException.class, () -> pointer.saveData(settings(room, 0, "ptr", "10"), null))
                        .getMessage());
    }

    @Test
    void itNeedsAUniqueName() throws Exception {
        WiredExtraPointerVariable pointer = new WiredExtraPointerVariable(9, 1, mock(Item.class), "", 0, 0);
        Room room = room(contextVariable(ACCESSOR, true), pointer);

        assertEquals(
                "wiredfurni.error.variables.name_uniq",
                assertThrows(WiredSaveException.class, () -> pointer.saveData(settings(room, 0, "target", "5"), null))
                        .getMessage());
    }

    @Test
    void itIsTheOfficialVariableCodeNine() {
        assertEquals(9, WiredAddonCodes.officialVariable(WiredExtraPointerVariable.CODE));
        assertTrue(WiredHolders.isVariable(new WiredExtraPointerVariable(9, 1, mock(Item.class), "", 0, 0)));
    }
}
