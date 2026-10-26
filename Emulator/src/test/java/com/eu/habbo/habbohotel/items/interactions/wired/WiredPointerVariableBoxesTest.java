package com.eu.habbo.habbohotel.items.interactions.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionHasVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraContextVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraPointerVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerVariableChanged;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomFurniVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableScope;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredValueVariable;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableTargets;
import com.eu.habbo.messages.incoming.wired.WiredTriggerSaveException;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** Every box that uses a variable works on the variable a pointer stands for in the running stack. */
class WiredPointerVariableBoxesTest {
    private static final int ACCESSOR = 5;
    private static final int GLOBAL_POINTER = 9;
    private static final int FURNI_POINTER = 8;
    private static final int TARGET = 50;
    private static final int OTHER = 51;

    private final RoomVariableManager roomVariables = mock(RoomVariableManager.class);
    private final RoomFurniVariableManager furniVariables = mock(RoomFurniVariableManager.class);
    private final Room room;

    WiredPointerVariableBoxesTest() throws Exception {
        this.room = room(
                contextVariable(ACCESSOR),
                pointer(GLOBAL_POINTER, WiredVariableTargets.OFFICIAL_GLOBAL),
                pointer(FURNI_POINTER, WiredVariableTargets.OFFICIAL_FURNI));
        WiredVariableDefinitionInfo info = new WiredVariableDefinitionInfo(TARGET, "hp", true, 0, false, false);
        when(this.roomVariables.getDefinitionInfo(TARGET)).thenReturn(info);
        when(this.roomVariables.getDefinitionInfo(OTHER))
                .thenReturn(new WiredVariableDefinitionInfo(OTHER, "max", true, 0, false, false));
        // As the managers list a pointer: a writable variable that needs a stack.
        when(this.roomVariables.getDefinitionInfo(GLOBAL_POINTER))
                .thenReturn(new WiredVariableDefinitionInfo(GLOBAL_POINTER, "ptr", true, 0, false, false, false, true));
        when(this.roomVariables.getCurrentValue(TARGET)).thenReturn(3);
        when(this.roomVariables.getCurrentValue(OTHER)).thenReturn(7);
        when(this.roomVariables.hasVariable(TARGET)).thenReturn(true);
        when(this.furniVariables.getDefinitionInfo(TARGET)).thenReturn(info);
        when(this.furniVariables.getDefinitionInfo(FURNI_POINTER))
                .thenReturn(new WiredVariableDefinitionInfo(FURNI_POINTER, "fp", true, 0, false, false, false, true));
        when(this.room.getRoomVariableManager()).thenReturn(this.roomVariables);
        when(this.room.getFurniVariableManager()).thenReturn(this.furniVariables);
    }

    private static WiredExtraContextVariable contextVariable(int id) throws Exception {
        WiredExtraContextVariable variable = new WiredExtraContextVariable(id, 1, mock(Item.class), "", 0, 0);
        variable.loadWiredData(data("{\"variableName\":\"target\",\"hasValue\":true}"), null);
        return variable;
    }

    private static WiredExtraPointerVariable pointer(int id, int sourceType) throws Exception {
        WiredExtraPointerVariable pointer = new WiredExtraPointerVariable(id, 1, mock(Item.class), "", 0, 0);
        pointer.loadWiredData(
                data("{\"variableName\":\"ptr" + id + "\",\"sourceType\":" + sourceType + ",\"accessorVariableId\":"
                        + ACCESSOR + "}"),
                null);
        return pointer;
    }

    private static ResultSet data(String json) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(json);
        return set;
    }

    private static Room room(InteractionWiredExtra... boxes) {
        Room room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        Set<InteractionWiredExtra> extras = new LinkedHashSet<>(List.of(boxes));
        when(room.getRoomSpecialTypes()).thenReturn(special);
        when(special.getExtras()).thenReturn(extras);
        when(special.getExtras(anyInt(), anyInt())).thenReturn(new LinkedHashSet<>());
        for (InteractionWiredExtra box : boxes)
            when(special.getExtra(box.getId())).thenReturn(box);
        return room;
    }

    /** A running stack; the accessor holds {@code target} (null: it was never given). */
    private WiredContext stack(Integer target) {
        WiredContext ctx = mock(WiredContext.class);
        WiredContextVariableScope scope = new WiredContextVariableScope();
        if (target != null) scope.assignValue(ACCESSOR, target, true);
        when(ctx.room()).thenReturn(this.room);
        when(ctx.contextVariables()).thenReturn(scope);
        return ctx;
    }

    private WiredEffectChangeVariableValue change(String json) throws Exception {
        WiredEffectChangeVariableValue box = new WiredEffectChangeVariableValue(20, 1, null, "", 0, 0);
        box.loadWiredData(data(json), this.room);
        // A delayed box resolves its pointers when it runs; without a delay Habbo ignores them.
        delay(box, 1);
        return box;
    }

    @Test
    void withoutADelayAChangeThroughAPointerChangesNothing() throws Exception {
        WiredEffectChangeVariableValue destination = change(
                "{\"destinationTargetType\":3,\"destinationVariableToken\":\"custom:9\",\"destinationVariableItemId\":9,"
                        + "\"operation\":1,\"referenceMode\":0,\"referenceConstantValue\":4}");
        delay(destination, 0);
        destination.execute(stack(TARGET));

        WiredEffectChangeVariableValue reference = change(
                "{\"destinationTargetType\":3,\"destinationVariableToken\":\"custom:50\",\"destinationVariableItemId\":50,"
                        + "\"operation\":0,\"referenceMode\":1,\"referenceTargetType\":3,"
                        + "\"referenceVariableToken\":\"custom:9\",\"referenceVariableItemId\":9}");
        delay(reference, 0);
        reference.execute(stack(OTHER));

        verify(this.roomVariables, never()).updateVariableValue(anyInt(), anyInt());
    }

    @Test
    void changeVariableWritesTheVariableThePointerStandsFor() throws Exception {
        WiredEffectChangeVariableValue box = change(
                "{\"destinationTargetType\":3,\"destinationVariableToken\":\"custom:9\",\"destinationVariableItemId\":9,"
                        + "\"operation\":1,\"referenceMode\":0,\"referenceConstantValue\":4}");

        box.execute(stack(TARGET));

        verify(this.roomVariables).updateVariableValue(TARGET, 7);
        verify(this.roomVariables, never()).updateVariableValue(eq(GLOBAL_POINTER), anyInt());
    }

    @Test
    void aPointerThatStandsForNothingChangesNothing() throws Exception {
        WiredEffectChangeVariableValue box = change(
                "{\"destinationTargetType\":3,\"destinationVariableToken\":\"custom:9\",\"destinationVariableItemId\":9,"
                        + "\"operation\":0,\"referenceMode\":0,\"referenceConstantValue\":4}");

        box.execute(stack(null));
        box.execute(stack(12345));

        verify(this.roomVariables, never()).updateVariableValue(anyInt(), anyInt());
    }

    @Test
    void changeVariableReadsAPointerReferenceAsTheVariableItStandsFor() throws Exception {
        WiredEffectChangeVariableValue box = change(
                "{\"destinationTargetType\":3,\"destinationVariableToken\":\"custom:50\",\"destinationVariableItemId\":50,"
                        + "\"operation\":0,\"referenceMode\":1,\"referenceTargetType\":3,"
                        + "\"referenceVariableToken\":\"custom:9\",\"referenceVariableItemId\":9}");

        box.execute(stack(OTHER));

        verify(this.roomVariables).updateVariableValue(TARGET, 7);
    }

    @Test
    void giveVariableGivesTheVariableThePointerStandsFor() throws Exception {
        WiredEffectGiveVariable box = new WiredEffectGiveVariable(21, 1, null, "", 0, 0);
        box.loadWiredData(
                data("{\"variableItemId\":8,\"targetType\":1,\"overrideExisting\":true,\"initialValue\":6,"
                        + "\"userSource\":0,\"furniSource\":" + WiredSourceUtil.SOURCE_SELECTOR + "}"),
                this.room);
        delay(box, 1);
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(77);

        try (MockedStatic<WiredSourceUtil> sources = mockStatic(WiredSourceUtil.class, Mockito.CALLS_REAL_METHODS)) {
            sources.when(() -> WiredSourceUtil.resolveItems(any(), eq(WiredSourceUtil.SOURCE_SELECTOR), any()))
                    .thenReturn(List.of(item));
            box.execute(stack(TARGET));
        }

        verify(this.furniVariables).assignVariable(item, TARGET, 6, true);
        verify(this.furniVariables, never())
                .assignVariable(any(HabboItem.class), eq(FURNI_POINTER), any(), anyBoolean());
    }

    @Test
    void hasVariableChecksTheVariableThePointerStandsFor() throws Exception {
        WiredConditionHasVariable box = new WiredConditionHasVariable(22, 1, null, "", 0, 0);
        box.loadWiredData(data("{\"targetType\":3,\"variableToken\":\"custom:9\",\"variableItemId\":9}"), this.room);

        assertTrue(box.evaluate(stack(TARGET)));
        assertFalse(box.evaluate(stack(OTHER)));
        assertFalse(box.evaluate(stack(null)));
    }

    @Test
    void valueOrVariableInputsReadThePointer() {
        assertEquals(
                7,
                WiredValueVariable.read(
                        stack(OTHER),
                        this.room,
                        WiredValueVariable.TARGET_GLOBAL,
                        GLOBAL_POINTER,
                        WiredSourceUtil.SOURCE_TRIGGER,
                        WiredSourceUtil.SOURCE_TRIGGER));
        assertNull(WiredValueVariable.read(
                stack(null),
                this.room,
                WiredValueVariable.TARGET_GLOBAL,
                GLOBAL_POINTER,
                WiredSourceUtil.SOURCE_TRIGGER,
                WiredSourceUtil.SOURCE_TRIGGER));
    }

    @Test
    void theVariableChangedTriggerRefusesAPointer() {
        WiredTriggerVariableChanged trigger = new WiredTriggerVariableChanged(23, 1, null, "", 0, 0);
        trigger.setRoomId(7);
        GameEnvironment environment = mock(GameEnvironment.class, Mockito.RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(7)).thenReturn(this.room);
        WiredSettings settings = new WiredSettings(
                new int[] {1, 1, 1, 7, 7, 0, WiredVariableTargets.OFFICIAL_GLOBAL}, "custom:9", new int[0], 0);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class, Mockito.CALLS_REAL_METHODS)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            WiredTriggerSaveException refused =
                    assertThrows(WiredTriggerSaveException.class, () -> trigger.saveData(settings, null));
            assertEquals("wiredfurni.error.variables.requires_execution_context", refused.getMessage());
        }
    }

    private static void delay(Object box, int pulses) throws Exception {
        java.lang.reflect.Method setDelay =
                com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect.class.getDeclaredMethod(
                        "setDelay", int.class);
        setDelay.setAccessible(true);
        setDelay.invoke(box, pulses);
    }
}
