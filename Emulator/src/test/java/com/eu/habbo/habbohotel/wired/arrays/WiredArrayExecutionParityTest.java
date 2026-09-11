package com.eu.habbo.habbohotel.wired.arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionVariableValueMatch;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraContextVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomArrayVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WiredArrayExecutionParityTest {
    @Test
    void captureFollowsTheSameEntryAcrossStructuralAndFieldChangesAndRejectsReplacement() {
        Fixture f = fixture();
        var scope = f.context.contextVariables();
        var owner = new WiredArrayRuntimeSupport.Owner(WiredArrayVariableType.CONTEXT, 77, null, null);
        var initial = scope.getArrayView(77, f.schema);
        scope.publishArrayCapture("entry", WiredArrayCaptureSnapshot.bound(f.array, owner, 0, 1, initial.getEntry(0)));

        scope.mutateArray(77, f.schema, WiredArrayStructuralOperation.INSERT, 0, 0, Map.of(1, 3L));
        assertEquals(1L, scope.readArrayCapture("entry.index", f.context));
        assertEquals(12L, scope.readArrayCapture("entry.value", f.context));

        try (var manager = mockStatic(WiredManager.class)) {
            manager.when(() -> WiredManager.tryConsumeArrayWork(any(), anyInt(), anyInt()))
                    .thenReturn(true);
            assertTrue(WiredArrayRuntimeSupport.mutateCapture(
                    f.context, "entry.value", WiredArrayNumericOperation.ASSIGN, Long.MAX_VALUE));
            assertEquals(Long.MAX_VALUE, scope.readArrayCapture("entry.value", f.context));
            assertEquals(3L, scope.getArrayView(77, f.schema).readField(0, 1));
            manager.verify(() -> WiredManager.handleEvent(any(WiredEvent.class)));
        }

        scope.mutateArray(77, f.schema, WiredArrayStructuralOperation.SET_ENTRY, 1, 0, Map.of(1, 55L));
        assertNull(scope.readArrayCapture("entry.value", f.context));
        assertFalse(WiredArrayRuntimeSupport.mutateCapture(
                f.context, "entry.value", WiredArrayNumericOperation.ASSIGN, 99L));
        assertEquals(55L, scope.getArrayView(77, f.schema).readField(1, 1));
    }

    @Test
    void contextCaptureForksResolveAgainstTheirOwnArrayValues() {
        Fixture f = fixture();
        var initial = f.context.contextVariables().getArrayView(77, f.schema);
        f.context
                .contextVariables()
                .publishArrayCapture(
                        "entry",
                        WiredArrayCaptureSnapshot.bound(
                                f.array,
                                new WiredArrayRuntimeSupport.Owner(WiredArrayVariableType.CONTEXT, 77, null, null),
                                0,
                                1,
                                initial.getEntry(0)));
        var fork = f.context.contextVariables().copy();
        WiredContext child = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, f.room)
                        .contextVariableScope(fork)
                        .build(),
                null,
                mock(WiredServices.class),
                new WiredState(100));
        child.contextVariables().mutateArrayField(77, 0, 1, WiredArrayNumericOperation.ASSIGN, 88L);

        assertEquals(12L, f.context.contextVariables().readArrayCapture("entry.value", f.context));
        assertEquals(88L, child.contextVariables().readArrayCapture("entry.value", child));
    }

    @Test
    void internalArrayOperandsKeepLongValuesAndHonorTheirSelectedSource() {
        Fixture f = fixture();
        var eventContext = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, f.room)
                        .arrayChange(WiredArrayChange.field(2, 1, 1, Long.MAX_VALUE, 3, 3))
                        .build(),
                null,
                mock(WiredServices.class),
                new WiredState(100));
        WiredArrayReference reference = new WiredArrayReference();
        reference.mode = WiredArrayReference.VARIABLE;
        reference.variableType = WiredArrayVariableType.CONTEXT.code();
        reference.variableToken = "internal:@array.new_value";
        assertTrue(WiredArrayEditorSupport.isValidReference(reference, f.room));
        assertEquals(
                Long.MAX_VALUE, WiredArrayRuntimeSupport.resolveReference(eventContext, List.of(), reference, null));

        HabboItem destination = mock(HabboItem.class);
        HabboItem trigger = mock(HabboItem.class);
        when(destination.getId()).thenReturn(3);
        when(trigger.getId()).thenReturn(9);
        reference.variableType = WiredArrayVariableType.FURNI.code();
        reference.variableToken = "internal:@id";
        reference.variableSource = WiredSourceUtil.SOURCE_TRIGGER;
        try (var sources = mockStatic(WiredSourceUtil.class)) {
            sources.when(() -> WiredSourceUtil.resolveItems(f.context, WiredSourceUtil.SOURCE_TRIGGER, List.of()))
                    .thenReturn(List.of(trigger));
            assertEquals(
                    9L,
                    WiredArrayRuntimeSupport.resolveReference(
                            f.context,
                            List.of(),
                            reference,
                            new WiredArrayRuntimeSupport.Owner(
                                    WiredArrayVariableType.FURNI,
                                    3,
                                    null,
                                    destination,
                                    WiredSourceUtil.SOURCE_SELECTOR)));
        }
    }

    @Test
    void scalarReadsIndexedArrayFieldsAndRejectsOutOfRangeOperands() throws Exception {
        Fixture f = fixture();
        f.context.contextVariables().assignValue(78, 2, false);
        WiredEffectChangeVariableValue effect = effect(f, """
                {"destinationTargetType":2,"destinationVariableToken":"custom:78",
                 "referenceMode":1,"referenceTargetType":2,"referenceVariableToken":"custom:77",
                 "arrayData":{"referenceAddress":{"mode":0,"value":0,"fieldId":1}}}
                """);
        try (var manager = mockStatic(WiredManager.class)) {
            manager.when(() -> WiredManager.tryConsumeArrayWork(any(), anyInt(), anyInt()))
                    .thenReturn(true);
            effect.execute(f.context);
            assertEquals(12, f.context.contextVariables().getValue(78));
            f.context.contextVariables().mutateArrayField(77, 0, 1, WiredArrayNumericOperation.ASSIGN, Long.MAX_VALUE);
            effect.execute(f.context);
            assertEquals(12, f.context.contextVariables().getValue(78));
        }
    }

    @Test
    void scalarComparisonCanReadALongArrayReference() throws Exception {
        Fixture f = fixture();
        f.context.contextVariables().assignValue(78, 2, false);
        f.context.contextVariables().mutateArrayField(77, 0, 1, WiredArrayNumericOperation.ASSIGN, Long.MAX_VALUE);
        WiredConditionVariableValueMatch condition = new WiredConditionVariableValueMatch(20, 1, null, "", 0, 0);
        ResultSet data = mock(ResultSet.class);
        when(data.getString("wired_data")).thenReturn("""
                {"targetType":2,"variableToken":"custom:78","comparison":4,
                 "referenceMode":1,"referenceTargetType":2,"referenceVariableToken":"custom:77",
                 "arrayData":{"referenceAddress":{"mode":0,"value":0,"fieldId":1}}}
                """);
        condition.loadWiredData(data, f.room);
        assertTrue(condition.evaluate(f.context));
    }

    @Test
    void changeVariableWritesCapturedFieldsWithoutNarrowingTheirOperands() throws Exception {
        Fixture f = fixture();
        var current = f.context.contextVariables().getArrayView(77, f.schema);
        f.context
                .contextVariables()
                .publishArrayCapture(
                        "entry",
                        WiredArrayCaptureSnapshot.bound(
                                f.array,
                                new WiredArrayRuntimeSupport.Owner(WiredArrayVariableType.CONTEXT, 77, null, null),
                                0,
                                1,
                                current.getEntry(0)));
        WiredEffectChangeVariableValue effect = effect(f, """
                {"destinationTargetType":2,"destinationVariableToken":"internal:entry.value",
                 "referenceMode":1,"referenceTargetType":2,"referenceVariableToken":"custom:77",
                 "arrayData":{"referenceAddress":{"mode":0,"value":1,"fieldId":1}}}
                """);
        f.context
                .contextVariables()
                .mutateArray(77, f.schema, WiredArrayStructuralOperation.APPEND, 0, 0, Map.of(1, Long.MAX_VALUE));
        try (var manager = mockStatic(WiredManager.class)) {
            manager.when(() -> WiredManager.tryConsumeArrayWork(any(), anyInt(), anyInt()))
                    .thenReturn(true);
            effect.execute(f.context);
            assertEquals(Long.MAX_VALUE, f.context.contextVariables().readArrayCapture("entry.value", f.context));
        }
    }

    @Test
    void capturedUserMutationEventsRetainTheCapturedOwnerRatherThanTheOriginalActor() {
        Fixture f = fixture();
        WiredExtraUserVariable definition = mock(WiredExtraUserVariable.class);
        when(f.room.getRoomSpecialTypes().getExtra(79)).thenReturn(definition);
        when(definition.getId()).thenReturn(79);
        when(definition.isArray()).thenReturn(true);
        when(definition.isArrayWritable()).thenReturn(true);
        when(definition.isArraySourceValid()).thenReturn(true);
        when(definition.getArrayVariableType()).thenReturn(WiredArrayVariableType.USER);
        when(definition.getArrayDefinition()).thenReturn(f.schema);
        RoomUnit capturedUser = mock(RoomUnit.class);
        Habbo habbo = mock(Habbo.class);
        when(f.room.getHabbo(90)).thenReturn(habbo);
        when(habbo.getRoomUnit()).thenReturn(capturedUser);
        RoomArrayVariableManager arrays = mock(RoomArrayVariableManager.class);
        when(f.room.getArrayVariableManager()).thenReturn(arrays);
        WiredArrayView value = f.context.contextVariables().getArrayView(77, f.schema);
        when(arrays.getValue(definition, 90)).thenReturn(value);
        long entryId = value.getEntry(0).getRuntimeId();
        when(arrays.mutateCapturedField(definition, 90, entryId, 1, WiredArrayNumericOperation.ASSIGN, 31))
                .thenReturn(new RoomArrayVariableManager.CapturedFieldMutationOutcome(
                        new RoomArrayVariableManager.FieldMutationOutcome(
                                WiredArrayMutationResult.SUCCESS, value, 12, 31, false),
                        0));
        f.context
                .contextVariables()
                .publishArrayCapture(
                        "user",
                        WiredArrayCaptureSnapshot.bound(
                                definition,
                                new WiredArrayRuntimeSupport.Owner(WiredArrayVariableType.USER, 90, capturedUser, null),
                                0,
                                1,
                                value.getEntry(0)));
        try (var manager = mockStatic(WiredManager.class)) {
            manager.when(() -> WiredManager.tryConsumeArrayWork(any(), anyInt(), anyInt()))
                    .thenReturn(true);
            assertTrue(WiredArrayRuntimeSupport.mutateCapture(
                    f.context, "user.value", WiredArrayNumericOperation.ASSIGN, 31));
            manager.verify(() ->
                    WiredManager.handleEvent(argThat(event -> event.getActor().orElse(null) == capturedUser
                            && event.getVariableDefinitionItemId() == 79
                            && event.getArrayChange().newValue() == 31)));
        }
        when(f.room.getHabbo(90)).thenReturn(null);
        assertFalse(
                WiredArrayRuntimeSupport.mutateCapture(f.context, "user.value", WiredArrayNumericOperation.ASSIGN, 32));
    }

    @Test
    void arrayReferencesUseSecondarySelectedFurnitureAndRejectExcessOwners() {
        Fixture f = fixture();
        WiredExtraFurniVariable definition = mock(WiredExtraFurniVariable.class);
        when(f.room.getRoomSpecialTypes().getExtra(79)).thenReturn(definition);
        when(definition.getId()).thenReturn(79);
        when(definition.isArray()).thenReturn(true);
        when(definition.getArrayVariableType()).thenReturn(WiredArrayVariableType.FURNI);
        when(definition.getArrayDefinition()).thenReturn(f.schema);
        RoomArrayVariableManager arrays = mock(RoomArrayVariableManager.class);
        when(f.room.getArrayVariableManager()).thenReturn(arrays);
        HabboItem selected = mock(HabboItem.class);
        when(selected.getId()).thenReturn(9);
        when(arrays.getValue(definition, 9))
                .thenReturn(f.context.contextVariables().getArrayView(77, f.schema));
        WiredArrayReference reference = new WiredArrayReference();
        reference.mode = WiredArrayReference.VARIABLE;
        reference.variableType = WiredArrayVariableType.FURNI.code();
        reference.variableToken = "custom:79";
        reference.variableSource = 101;
        reference.address.fieldId = 1;
        try (var sources = mockStatic(WiredSourceUtil.class)) {
            sources.when(() ->
                            WiredSourceUtil.resolveItems(f.context, WiredSourceUtil.SOURCE_SELECTED, List.of(selected)))
                    .thenReturn(List.of(selected));
            assertEquals(12L, WiredArrayRuntimeSupport.resolveReference(f.context, List.of(selected), reference, null));
            List<HabboItem> tooMany = java.util.stream.IntStream.rangeClosed(
                            1, WiredArraySettings.maxOwnersPerExecution() + 1)
                    .mapToObj(id -> {
                        HabboItem item = mock(HabboItem.class);
                        when(item.getId()).thenReturn(id);
                        return item;
                    })
                    .toList();
            sources.when(() -> WiredSourceUtil.resolveItems(f.context, WiredSourceUtil.SOURCE_SELECTED, tooMany))
                    .thenReturn(tooMany);
            assertTrue(WiredArrayRuntimeSupport.resolveOwners(f.context, tooMany, definition, 101)
                    .isEmpty());
        }
    }

    @Test
    void longBitOperationsPreserveTheSignBitAndRejectInvalidBitPositions() {
        assertEquals(Long.MIN_VALUE, WiredArrayNumericOperation.SET_BIT.apply(0, 63));
        assertEquals(1, WiredArrayNumericOperation.GET_BIT.apply(Long.MIN_VALUE, 63));
        assertEquals(63, WiredArrayNumericOperation.NEXT_HIGH_BIT.apply(Long.MIN_VALUE, 0));
        assertEquals(63, WiredArrayNumericOperation.PREVIOUS_HIGH_BIT.apply(Long.MIN_VALUE, 63));
        assertEquals(62, WiredArrayNumericOperation.PREVIOUS_LOW_BIT_EXCLUSIVE.apply(Long.MIN_VALUE, 63));
        assertEquals(-1, WiredArrayNumericOperation.NEXT_HIGH_BIT_EXCLUSIVE.apply(Long.MIN_VALUE, 63));
        assertEquals(12, WiredArrayNumericOperation.SET_BIT.apply(12, Long.MAX_VALUE));
        assertEquals(-1, WiredArrayNumericOperation.NEXT_HIGH_BIT.apply(12, Long.MAX_VALUE));
    }

    private static WiredEffectChangeVariableValue effect(Fixture f, String json) throws Exception {
        WiredEffectChangeVariableValue effect = new WiredEffectChangeVariableValue(10, 1, null, "", 0, 0);
        ResultSet data = mock(ResultSet.class);
        when(data.getString("wired_data")).thenReturn(json);
        effect.loadWiredData(data, f.room);
        return effect;
    }

    private static Fixture fixture() {
        Room room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        WiredExtraContextVariable array = mock(WiredExtraContextVariable.class);
        WiredExtraContextVariable scalar = mock(WiredExtraContextVariable.class);
        when(room.getId()).thenReturn(7);
        when(room.getRoomSpecialTypes()).thenReturn(special);
        when(special.getExtra(77)).thenReturn(array);
        when(special.getExtra(78)).thenReturn(scalar);
        WiredVariableDefinitionData data = new WiredVariableDefinitionData();
        data.valueShape = "array";
        data.arrayFormat = "simple";
        data.arrayMode = "list";
        data.maxEntries = 4;
        WiredArrayDefinition schema = WiredArrayDefinition.fromData(data, 4);
        when(array.getId()).thenReturn(77);
        when(array.isArray()).thenReturn(true);
        when(array.isArrayWritable()).thenReturn(true);
        when(array.isArraySourceValid()).thenReturn(true);
        when(array.getArrayVariableType()).thenReturn(WiredArrayVariableType.CONTEXT);
        when(array.getArrayDefinition()).thenReturn(schema);
        when(scalar.getId()).thenReturn(78);
        when(scalar.getVariableName()).thenReturn("scalar");
        when(scalar.hasValue()).thenReturn(true);
        WiredContext context = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(100));
        context.contextVariables().giveArray(77, schema, false);
        context.contextVariables().mutateArray(77, schema, WiredArrayStructuralOperation.APPEND, 0, 0, Map.of(1, 12L));
        return new Fixture(room, array, schema, context);
    }

    private record Fixture(
            Room room, WiredExtraContextVariable array, WiredArrayDefinition schema, WiredContext context) {}
}
