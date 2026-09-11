package com.eu.habbo.habbohotel.wired.arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectRemoveVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraContextVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.RoomRightLevels;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class WiredVariableAssignmentParityTest {
    @Test
    void giveOverrideEmitsClearAndPreservesThePreviousLength() throws Exception {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(7);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(special);
        WiredExtraContextVariable definition = mock(WiredExtraContextVariable.class);
        when(special.getExtra(77)).thenReturn(definition);
        when(definition.getId()).thenReturn(77);
        when(definition.isArray()).thenReturn(true);
        when(definition.isArrayWritable()).thenReturn(true);
        when(definition.isArraySourceValid()).thenReturn(true);
        when(definition.getArrayVariableType()).thenReturn(WiredArrayVariableType.CONTEXT);
        WiredVariableDefinitionData data = new WiredVariableDefinitionData();
        data.valueShape = "array";
        data.maxEntries = 8;
        WiredArrayDefinition schema = WiredArrayDefinition.fromData(data, 8);
        when(definition.getArrayDefinition()).thenReturn(schema);
        WiredContext context = context(room, null);
        context.contextVariables().giveArray(77, schema, false);
        context.contextVariables().mutateArray(77, schema, WiredArrayStructuralOperation.APPEND, 0, 0, Map.of(1, 9L));
        WiredEffectGiveVariable give = new WiredEffectGiveVariable(10, 1, null, "", 0, 0);
        give.loadWiredData(stored("{\"variableItemId\":77,\"targetType\":2,\"overrideExisting\":true}"), room);
        List<WiredEvent> events = new ArrayList<>();
        try (var manager = mockStatic(WiredManager.class, CALLS_REAL_METHODS)) {
            manager.when(() -> WiredManager.tryConsumeArrayWork(any(), anyInt(), anyInt()))
                    .thenReturn(true);
            manager.when(() -> WiredManager.handleEvent(any(WiredEvent.class))).thenAnswer(invocation -> {
                events.add(invocation.getArgument(0));
                return true;
            });
            give.execute(context);
        }
        assertEquals(1, events.size());
        assertEquals(
                WiredArrayChangeType.ARRAY_CLEARED,
                events.getFirst().getArrayChange().changeType());
        assertEquals(1, events.getFirst().getArrayChange().oldLength());
        assertEquals(0, events.getFirst().getArrayChange().newLength());
        assertFalse(events.getFirst().isVariableCreated());
    }

    @Test
    void giveAndRemoveRightsUseTheSelectedUserAndEmitAuthoritativeValues() throws Exception {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getId()).thenReturn(44);
        when(room.getHabbo(unit)).thenReturn(habbo);
        when(room.getGuildRightLevel(habbo)).thenReturn(RoomRightLevels.NONE);
        AtomicBoolean rights = new AtomicBoolean(false);
        when(room.hasRights(habbo)).thenAnswer(invocation -> rights.get());
        doAnswer(invocation -> {
                    rights.set(true);
                    return null;
                })
                .when(room)
                .giveRights(44);
        doAnswer(invocation -> {
                    rights.set(false);
                    return null;
                })
                .when(room)
                .removeRights(44);
        WiredContext context = context(room, unit);
        WiredEffectGiveVariable give = new WiredEffectGiveVariable(10, 1, null, "", 0, 0);
        WiredEffectRemoveVariable remove = new WiredEffectRemoveVariable(11, 1, null, "", 0, 0);
        give.loadWiredData(stored("{\"internalVariableKey\":\"@has_rights\",\"targetType\":0}"), room);
        remove.loadWiredData(stored("{\"internalVariableKey\":\"@has_rights\",\"targetType\":0}"), room);
        List<WiredEvent> events = new ArrayList<>();
        try (var sources = mockStatic(WiredSourceUtil.class);
                var manager = mockStatic(WiredManager.class, CALLS_REAL_METHODS)) {
            sources.when(() -> WiredSourceUtil.resolveUsers(context, 0)).thenReturn(List.of(unit));
            manager.when(() -> WiredManager.dispatchEffectTriggeredEvent(any(WiredEvent.class)))
                    .thenAnswer(invocation -> {
                        events.add(invocation.getArgument(0));
                        return true;
                    });
            give.execute(context);
            assertTrue(rights.get());
            remove.execute(context);
            assertFalse(rights.get());
        }
        assertEquals(2, events.size());
        assertEquals("@has_rights", events.getFirst().getInternalVariableKey());
        assertEquals(0, events.getFirst().getOldVariableValue());
        assertEquals(1, events.getFirst().getNewVariableValue());
        assertEquals(1, events.getLast().getOldVariableValue());
        assertEquals(0, events.getLast().getNewVariableValue());
        assertSame(unit, events.getFirst().getActor().orElseThrow());
    }

    @Test
    void builtinSaveAndReopenPreservesCanonicalTokenAndRejectsInvalidTargets() throws Exception {
        Room room = mock(Room.class);
        GameEnvironment environment = mock(GameEnvironment.class);
        RoomManager rooms = mock(RoomManager.class);
        when(environment.getRoomManager()).thenReturn(rooms);
        when(rooms.getRoom(7)).thenReturn(room);
        WiredEffectGiveVariable give = new WiredEffectGiveVariable(10, 1, null, "", 0, 0);
        give.setRoomId(7);
        WiredEffectRemoveVariable remove = new WiredEffectRemoveVariable(11, 1, null, "", 0, 0);
        remove.setRoomId(7);
        try (var emulator = mockStatic(Emulator.class, CALLS_REAL_METHODS)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            assertTrue(give.saveData(
                    new WiredSettings(new int[] {0, 1, 42, 0, 0}, "internal:@effect", new int[0], 0), null));
            assertTrue(remove.saveData(
                    new WiredSettings(new int[] {0, 0, 0}, "internal:@has_rights", new int[0], 0), null));
            assertThrows(
                    WiredSaveException.class,
                    () -> give.saveData(new WiredSettings(new int[] {1}, "internal:@effect", new int[0], 0), null));
            assertThrows(
                    WiredSaveException.class,
                    () -> remove.saveData(new WiredSettings(new int[] {0}, "internal:@effect", new int[0], 0), null));
        }
        String saved = give.getWiredData();
        WiredEffectGiveVariable reopened = new WiredEffectGiveVariable(12, 1, null, "", 0, 0);
        reopened.loadWiredData(stored(saved), room);
        assertEquals(saved, reopened.getWiredData());
        assertTrue(saved.contains("@effect_id"));
        assertEquals(42, reopened.getInitialValue());
    }

    private static ResultSet stored(String json) throws Exception {
        ResultSet data = mock(ResultSet.class);
        when(data.getString("wired_data")).thenReturn(json);
        return data;
    }

    private static WiredContext context(Room room, RoomUnit actor) {
        return new WiredContext(
                WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                        .actor(actor)
                        .build(),
                null,
                mock(WiredServices.class),
                new WiredState(100));
    }
}
