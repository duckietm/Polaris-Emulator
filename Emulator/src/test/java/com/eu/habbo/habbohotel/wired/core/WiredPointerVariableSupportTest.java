package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraContextVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraPointerVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomFurniVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableTargets;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A pointer stands for the variable whose WIRED box id its accessor context variable holds. */
class WiredPointerVariableSupportTest {
    static final int ACCESSOR = 5;
    static final int POINTER = 9;
    static final int FURNI_VARIABLE = 40;
    static final int USER_VARIABLE = 41;

    private final RoomFurniVariableManager furniVariables = mock(RoomFurniVariableManager.class);
    private final RoomUserVariableManager userVariables = mock(RoomUserVariableManager.class);

    static WiredExtraPointerVariable pointer(int id, String name, int sourceType, int accessor) throws Exception {
        WiredExtraPointerVariable pointer = new WiredExtraPointerVariable(id, 1, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data"))
                .thenReturn("{\"variableName\":\"" + name + "\",\"sourceType\":" + sourceType
                        + ",\"accessorVariableId\":" + accessor + "}");
        pointer.loadWiredData(set, null);
        return pointer;
    }

    static WiredExtraContextVariable contextVariable(int id, String name, boolean hasValue) throws Exception {
        WiredExtraContextVariable variable = new WiredExtraContextVariable(id, 1, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data"))
                .thenReturn("{\"variableName\":\"" + name + "\",\"hasValue\":" + hasValue + "}");
        variable.loadWiredData(set, null);
        return variable;
    }

    static Room room(InteractionWiredExtra... boxes) {
        Room room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        Set<InteractionWiredExtra> extras = new LinkedHashSet<>(List.of(boxes));
        when(room.getRoomSpecialTypes()).thenReturn(special);
        when(special.getExtras()).thenReturn(extras);
        for (InteractionWiredExtra box : boxes)
            when(special.getExtra(box.getId())).thenReturn(box);
        when(room.getRoomVariableManager()).thenReturn(mock(RoomVariableManager.class));
        return room;
    }

    /** A running stack whose accessor holds {@code value} (null: the accessor was never given). */
    static WiredContext stack(Room room, Integer value) {
        WiredContext ctx = mock(WiredContext.class);
        WiredContextVariableScope scope = new WiredContextVariableScope();
        if (value != null) scope.assignValue(ACCESSOR, value, true);
        when(ctx.room()).thenReturn(room);
        when(ctx.contextVariables()).thenReturn(scope);
        return ctx;
    }

    private Room furniRoom(InteractionWiredExtra... boxes) {
        Room room = room(boxes);
        WiredVariableDefinitionInfo info = new WiredVariableDefinitionInfo(FURNI_VARIABLE, "hp", true, 0, false, false);
        when(this.furniVariables.getDefinitionInfo(anyInt())).thenReturn(null);
        when(this.furniVariables.getDefinitionInfo(FURNI_VARIABLE)).thenReturn(info);
        when(this.userVariables.getDefinitionInfo(anyInt())).thenReturn(null);
        when(this.userVariables.getDefinitionInfo(USER_VARIABLE))
                .thenReturn(new WiredVariableDefinitionInfo(USER_VARIABLE, "score", true, 0, false, false));
        when(room.getFurniVariableManager()).thenReturn(this.furniVariables);
        when(room.getUserVariableManager()).thenReturn(this.userVariables);
        return room;
    }

    @Test
    void aVariableThatIsNoPointerIsUsedAsItIs() throws Exception {
        Room room = furniRoom(contextVariable(ACCESSOR, "target", true));

        assertEquals(FURNI_VARIABLE, WiredPointerVariableSupport.resolve(stack(room, null), room, FURNI_VARIABLE));
        assertEquals(FURNI_VARIABLE, WiredPointerVariableSupport.resolve(null, room, FURNI_VARIABLE));
    }

    @Test
    void aPointerStandsForTheVariableWhoseBoxIdTheAccessorHolds() throws Exception {
        Room room = furniRoom(
                contextVariable(ACCESSOR, "target", true),
                pointer(POINTER, "ptr", WiredVariableTargets.OFFICIAL_FURNI, ACCESSOR));

        assertEquals(FURNI_VARIABLE, WiredPointerVariableSupport.resolve(stack(room, FURNI_VARIABLE), room, POINTER));
    }

    @Test
    void aPointerStandsForNothingWithoutAStackOrAnAccessorValue() throws Exception {
        Room room = furniRoom(
                contextVariable(ACCESSOR, "target", true),
                pointer(POINTER, "ptr", WiredVariableTargets.OFFICIAL_FURNI, ACCESSOR));

        assertEquals(0, WiredPointerVariableSupport.resolve(null, room, POINTER));
        assertEquals(0, WiredPointerVariableSupport.resolve(stack(room, null), room, POINTER));
        assertEquals(0, WiredPointerVariableSupport.resolve(stack(room, 0), room, POINTER));
        assertEquals(0, WiredPointerVariableSupport.resolve(stack(room, 12345), room, POINTER));
    }

    @Test
    void theTargetMustBeAVariableOfThePointersSourceType() throws Exception {
        Room room = furniRoom(
                contextVariable(ACCESSOR, "target", true),
                pointer(POINTER, "ptr", WiredVariableTargets.OFFICIAL_FURNI, ACCESSOR));

        // A user variable box id in a furni pointer.
        assertEquals(0, WiredPointerVariableSupport.resolve(stack(room, USER_VARIABLE), room, POINTER));
    }

    @Test
    void aPointerNeverStandsForAPointer() throws Exception {
        WiredExtraPointerVariable other = pointer(10, "other", WiredVariableTargets.OFFICIAL_FURNI, ACCESSOR);
        Room room = furniRoom(
                contextVariable(ACCESSOR, "target", true),
                pointer(POINTER, "ptr", WiredVariableTargets.OFFICIAL_FURNI, ACCESSOR),
                other);
        when(this.furniVariables.getDefinitionInfo(10)).thenReturn(WiredPointerVariableSupport.definitionInfo(other));

        assertEquals(0, WiredPointerVariableSupport.resolve(stack(room, 10), room, POINTER));
        assertEquals(0, WiredPointerVariableSupport.resolve(stack(room, POINTER), room, POINTER));
    }

    @Test
    void theAccessorMustBeAContextVariableWithAValue() throws Exception {
        Room valueless = furniRoom(
                contextVariable(ACCESSOR, "target", false),
                pointer(POINTER, "ptr", WiredVariableTargets.OFFICIAL_FURNI, ACCESSOR));

        assertFalse(WiredPointerVariableSupport.isValidAccessor(valueless, ACCESSOR));
        assertEquals(0, WiredPointerVariableSupport.resolve(stack(valueless, FURNI_VARIABLE), valueless, POINTER));

        Room valid = furniRoom(contextVariable(ACCESSOR, "target", true));
        assertTrue(WiredPointerVariableSupport.isValidAccessor(valid, ACCESSOR));
        assertFalse(WiredPointerVariableSupport.isValidAccessor(valid, FURNI_VARIABLE));
    }

    @Test
    void aPointerIsListedUnderItsSourceTypeAsNeedingAStack() throws Exception {
        Room room = room(
                pointer(POINTER, "ptr", WiredVariableTargets.OFFICIAL_USER, ACCESSOR),
                pointer(10, "", WiredVariableTargets.OFFICIAL_USER, ACCESSOR),
                pointer(11, "other", WiredVariableTargets.OFFICIAL_FURNI, ACCESSOR));

        List<WiredVariableDefinitionInfo> users =
                WiredPointerVariableSupport.definitionInfos(room, WiredVariableTargets.OFFICIAL_USER);
        assertEquals(1, users.size());
        WiredVariableDefinitionInfo info = users.get(0);
        assertEquals(POINTER, info.getItemId());
        assertEquals("ptr", info.getName());
        assertTrue(info.hasValue());
        assertFalse(info.isReadOnly());
        assertTrue(info.requiresExecutionContext());

        assertNull(WiredPointerVariableSupport.definitionInfo(room, POINTER, WiredVariableTargets.OFFICIAL_FURNI));
        assertTrue(WiredPointerVariableSupport.anyPointer(room, 0, 11));
        assertFalse(WiredPointerVariableSupport.anyPointer(room, 0, ACCESSOR));
    }
}
