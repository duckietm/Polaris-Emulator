package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomWiredRuntime;
import com.eu.habbo.habbohotel.rooms.WiredBoxFlashes;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredVariableChangeFold;
import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Without Execute In Order, the Change Variable boxes of one execution fold into one change per
 * variable and holder, in Habbo's order: set, power, multiply, divide, modulo, add, subtract.
 */
class WiredEffectChangeVariableValueFoldTest {

    @Test
    void setThenAddThenMultiplyGivesElevenAsOneChange() throws Exception {
        AtomicInteger stored = new AtomicInteger(1);
        Room room = roomWithVariable(stored);
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);

        // Box order: set 3, add 5, multiply 2.
        WiredEffectChangeVariableValue set = box(room, WiredEffectChangeVariableValue.OP_ASSIGN, 3);
        WiredEffectChangeVariableValue add = box(room, WiredEffectChangeVariableValue.OP_ADD, 5);
        WiredEffectChangeVariableValue mul = box(room, WiredEffectChangeVariableValue.OP_MUL, 2);

        try (WiredVariableChangeFold.Scope ignored = WiredVariableChangeFold.begin()) {
            set.execute(ctx);
            add.execute(ctx);
            mul.execute(ctx);
            // Nothing is written while the execution runs.
            verify(room.getRoomVariableManager(), never()).updateVariableValue(anyInt(), anyInt());
        }

        assertEquals(11, stored.get());
        verify(room.getRoomVariableManager(), times(1)).updateVariableValue(50, 11);
        assertFalse(WiredVariableChangeFold.isActive());
    }

    @Test
    void withExecuteInOrderEveryBoxAppliesItsOwnChange() throws Exception {
        AtomicInteger stored = new AtomicInteger(1);
        Room room = roomWithVariable(stored);
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);

        box(room, WiredEffectChangeVariableValue.OP_ASSIGN, 3).execute(ctx);
        box(room, WiredEffectChangeVariableValue.OP_ADD, 5).execute(ctx);
        box(room, WiredEffectChangeVariableValue.OP_MUL, 2).execute(ctx);

        assertEquals(16, stored.get());
        verify(room.getRoomVariableManager(), times(3)).updateVariableValue(anyInt(), anyInt());
    }

    @Test
    void theFoldOrderIsSetPowerMultiplyDivideModuloAddSubtractThenTheRestInBoxOrder() {
        WiredVariableChangeFold.Operation apply = WiredEffectChangeVariableValue::applyOperation;
        List<WiredVariableChangeFold.Change> changes = List.of(
                new WiredVariableChangeFold.Change(WiredEffectChangeVariableValue.OP_SUB, 1),
                new WiredVariableChangeFold.Change(WiredEffectChangeVariableValue.OP_MAX, 100),
                new WiredVariableChangeFold.Change(WiredEffectChangeVariableValue.OP_ADD, 4),
                new WiredVariableChangeFold.Change(WiredEffectChangeVariableValue.OP_MOD, 7),
                new WiredVariableChangeFold.Change(WiredEffectChangeVariableValue.OP_DIV, 2),
                new WiredVariableChangeFold.Change(WiredEffectChangeVariableValue.OP_MUL, 3),
                new WiredVariableChangeFold.Change(WiredEffectChangeVariableValue.OP_POW, 2),
                new WiredVariableChangeFold.Change(WiredEffectChangeVariableValue.OP_ASSIGN, 5),
                new WiredVariableChangeFold.Change(WiredEffectChangeVariableValue.OP_MIN, 50));

        // 5 -> ^2 25 -> *3 75 -> /2 37 -> %7 2 -> +4 6 -> -1 5 -> max 100 -> min 50.
        assertEquals(50, WiredVariableChangeFold.fold(0, changes, apply));
        assertEquals(
                5,
                WiredVariableChangeFold.fold(
                        0,
                        List.of(
                                new WiredVariableChangeFold.Change(WiredEffectChangeVariableValue.OP_SUB, 1),
                                new WiredVariableChangeFold.Change(WiredEffectChangeVariableValue.OP_ADD, 4),
                                new WiredVariableChangeFold.Change(WiredEffectChangeVariableValue.OP_ASSIGN, 2)),
                        apply));
    }

    @Test
    void foldsNestAndNothingIsHeldWithoutOne() {
        AtomicInteger outer = new AtomicInteger(0);
        AtomicInteger inner = new AtomicInteger(0);
        WiredVariableChangeFold.Operation apply = WiredEffectChangeVariableValue::applyOperation;

        assertFalse(WiredVariableChangeFold.defer("x", 1, 1, apply, outer::get, outer::set));

        try (WiredVariableChangeFold.Scope ignored = WiredVariableChangeFold.begin()) {
            assertTrue(WiredVariableChangeFold.defer("x", 1, 2, apply, outer::get, outer::set));
            try (WiredVariableChangeFold.Scope nested = WiredVariableChangeFold.begin()) {
                assertTrue(WiredVariableChangeFold.defer("y", 1, 7, apply, inner::get, inner::set));
            }
            assertEquals(7, inner.get());
            assertEquals(0, outer.get());
        }
        assertEquals(2, outer.get());
    }

    private static Room roomWithVariable(AtomicInteger stored) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(1);
        RoomVariableManager roomVariables = mock(RoomVariableManager.class);
        WiredVariableDefinitionInfo info = mock(WiredVariableDefinitionInfo.class);
        when(info.hasValue()).thenReturn(true);
        when(roomVariables.getDefinitionInfo(50)).thenReturn(info);
        when(roomVariables.getCurrentValue(50)).thenAnswer(invocation -> stored.get());
        when(roomVariables.updateVariableValue(anyInt(), anyInt())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(1));
            return true;
        });
        when(room.getRoomVariableManager()).thenReturn(roomVariables);
        when(room.getRoomSpecialTypes()).thenReturn(mock(RoomSpecialTypes.class));
        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        when(room.getWiredRuntime()).thenReturn(runtime);
        when(runtime.getBoxFlashes()).thenReturn(mock(WiredBoxFlashes.class));
        return room;
    }

    private static WiredEffectChangeVariableValue box(Room room, int operation, int constant) throws Exception {
        WiredEffectChangeVariableValue box = new WiredEffectChangeVariableValue(10, 1, null, "", 0, 0);
        ResultSet data = mock(ResultSet.class);
        when(data.getString("wired_data"))
                .thenReturn("{\"destinationTargetType\":3,\"destinationVariableToken\":\"custom:50\","
                        + "\"destinationVariableItemId\":50,\"operation\":" + operation
                        + ",\"referenceMode\":0,\"referenceConstantValue\":" + constant + "}");
        box.loadWiredData(data, room);
        return box;
    }
}
