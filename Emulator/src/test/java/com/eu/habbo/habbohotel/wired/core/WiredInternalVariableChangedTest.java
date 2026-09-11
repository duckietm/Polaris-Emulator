package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.games.wired.WiredGame;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerVariableChanged;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredVariableChangeOrigin;
import com.eu.habbo.messages.incoming.wired.WiredTriggerSaveException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WiredInternalVariableChangedTest {
    @Test
    void internalTriggerPersistsItsCanonicalKeyAndPreservesCustomMatching() throws Exception {
        Room room = mock(Room.class);
        GameEnvironment environment = mock(GameEnvironment.class);
        RoomManager rooms = mock(RoomManager.class);
        when(environment.getRoomManager()).thenReturn(rooms);
        when(rooms.getRoom(anyInt())).thenReturn(room);
        WiredTriggerVariableChanged trigger = new WiredTriggerVariableChanged(91, 1, null, "", 0, 0);
        WiredSettings settings = new WiredSettings(new int[] {0, 0, 1, 1, 1, 1, 0}, "internal:@effect", new int[0], 0);

        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            assertTrue(trigger.saveData(settings));
            settings.setStringParam("internal:@user_id");
            assertThrows(WiredTriggerSaveException.class, () -> trigger.saveData(settings));
            settings.setStringParam("internal:@has_rights");
            assertTrue(trigger.saveData(settings));
            settings.setStringParam("internal:@effect");
            assertTrue(trigger.saveData(settings));
        }

        ResultSet stored = mock(ResultSet.class);
        when(stored.getString("wired_data")).thenReturn(trigger.getWiredData());
        WiredTriggerVariableChanged reopened = new WiredTriggerVariableChanged(92, 1, null, "", 0, 0);
        reopened.loadWiredData(stored, room);
        WiredEvent matching = changed(room, 0, "@effect_id", 0, 42);
        assertTrue(reopened.matches(reopened, matching));
        assertFalse(reopened.matches(reopened, changed(room, 1, "@effect_id", 0, 42)));
        assertFalse(reopened.matches(reopened, changed(room, 0, "@handitem", 0, 42)));
        assertFalse(reopened.matches(
                reopened,
                WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                        .variableDefinitionItemId(92)
                        .variableTargetType(0)
                        .variableChangeKind(WiredEvent.VariableChangeKind.INCREASED)
                        .build()));
        assertTrue(reopened.getWiredData().contains("internal:@effect_id"));

        when(stored.getString("wired_data")).thenReturn("custom:92");
        reopened.loadWiredData(stored, room);
        assertFalse(reopened.matches(reopened, matching));
        assertTrue(reopened.matches(
                reopened,
                WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                        .variableDefinitionItemId(92)
                        .variableTargetType(0)
                        .variableChangeKind(WiredEvent.VariableChangeKind.INCREASED)
                        .build()));
    }

    @Test
    void explicitWritesEmitExactOldAndNewValuesIncludingUnchangedAndOrigin() {
        Room room = mock(Room.class);
        RoomUnit unit = new RoomUnit();
        RoomTile tile = new RoomTile((short) 0, (short) 0, (short) 0, RoomTileState.OPEN, true);
        unit.setCurrentLocation(tile);
        unit.setPreviousLocation(tile);
        unit.setBodyRotation(RoomUserRotation.NORTH);
        unit.setHeadRotation(RoomUserRotation.NORTH);
        List<WiredEvent> events = new ArrayList<>();
        int previousOrigin = WiredVariableChangeOrigin.enter(WiredVariableChangeOrigin.CREATOR_TOOLS);

        try (var manager = mockStatic(WiredManager.class)) {
            manager.when(() -> WiredManager.dispatchEffectTriggeredEvent(any(WiredEvent.class)))
                    .thenAnswer(call -> {
                        events.add(call.getArgument(0));
                        return true;
                    });
            assertTrue(WiredInternalVariableSupport.writeUserValue(room, unit, "@altitude", 125));
            assertTrue(WiredInternalVariableSupport.writeUserValue(room, unit, "@altitude", 25));
            assertTrue(WiredInternalVariableSupport.writeUserValue(room, unit, "@altitude", 25));
            assertFalse(WiredInternalVariableSupport.writeUserValue(room, unit, "@effect", -1));
        } finally {
            WiredVariableChangeOrigin.exit(previousOrigin);
        }

        assertEquals(3, events.size());
        assertEquals(
                List.of(0L, 125L, 25L),
                events.stream().map(WiredEvent::getOldVariableValue).toList());
        assertEquals(
                List.of(125L, 25L, 25L),
                events.stream().map(WiredEvent::getNewVariableValue).toList());
        assertEquals(
                List.of(
                        WiredEvent.VariableChangeKind.INCREASED,
                        WiredEvent.VariableChangeKind.DECREASED,
                        WiredEvent.VariableChangeKind.UNCHANGED),
                events.stream().map(WiredEvent::getVariableChangeKind).toList());
        assertTrue(events.stream()
                .allMatch(event -> event.getInternalVariableKey().equals("@altitude")
                        && event.getVariableTargetType() == 0
                        && event.getActor().orElse(null) == unit
                        && event.getVariableChangeOrigin() == WiredVariableChangeOrigin.CREATOR_TOOLS));
    }

    @Test
    void furnitureAndRoomWritesEmitTheActualStoredValueAndCorrectTarget() {
        Room room = mock(Room.class);
        HabboItem item = mock(HabboItem.class);
        when(item.getBaseItem()).thenReturn(mock(Item.class));
        AtomicReference<String> state = new AtomicReference<>("2");
        when(item.getExtradata()).thenAnswer(call -> state.get());
        doAnswer(call -> {
                    state.set(call.getArgument(0));
                    return null;
                })
                .when(item)
                .setExtradata(any());
        WiredGame game = mock(WiredGame.class);
        GameTeam team = new GameTeam(GameTeamColors.RED);
        team.addTeamScore(3);
        when(room.getGame(WiredGame.class)).thenReturn(game);
        when(game.getTeam(GameTeamColors.RED)).thenReturn(team);
        List<WiredEvent> events = new ArrayList<>();

        try (var manager = mockStatic(WiredManager.class)) {
            manager.when(() -> WiredManager.dispatchEffectTriggeredEvent(any(WiredEvent.class)))
                    .thenAnswer(call -> {
                        events.add(call.getArgument(0));
                        return true;
                    });
            assertTrue(WiredInternalVariableSupport.writeFurniValue(room, item, "@state", 5));
            assertTrue(WiredInternalVariableSupport.writeRoomValue(room, "@teams.red.score", 8));
            assertFalse(WiredInternalVariableSupport.writeRoomValue(room, "@teams.red.score", -1));
        }

        assertEquals(2, events.size());
        assertEquals(1, events.get(0).getVariableTargetType());
        assertEquals(item, events.get(0).getSourceItem().orElseThrow());
        assertEquals(2L, events.get(0).getOldVariableValue());
        assertEquals(5L, events.get(0).getNewVariableValue());
        assertEquals(3, events.get(1).getVariableTargetType());
        assertEquals("@team_red_score", events.get(1).getInternalVariableKey());
        assertEquals(3L, events.get(1).getOldVariableValue());
        assertEquals(8L, events.get(1).getNewVariableValue());
    }

    @Test
    void batchedPositionWritesEmitOnlyAfterMovementSucceeds() {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        RoomUnit unit = new RoomUnit();
        unit.setCurrentLocation(new RoomTile((short) 0, (short) 0, (short) 0, RoomTileState.OPEN, true));
        unit.setBodyRotation(RoomUserRotation.NORTH);
        unit.setHeadRotation(RoomUserRotation.NORTH);
        RoomTile target = new RoomTile((short) 3, (short) 4, (short) 0, RoomTileState.OPEN, true);
        when(room.getLayout()).thenReturn(layout);
        when(layout.getTile((short) 3, (short) 4)).thenReturn(target);
        List<WiredEvent> events = new ArrayList<>();

        try (var manager = mockStatic(WiredManager.class);
                var movement = mockStatic(WiredUserMovementHelper.class)) {
            manager.when(() -> WiredManager.dispatchEffectTriggeredEvent(any(WiredEvent.class)))
                    .thenAnswer(call -> {
                        events.add(call.getArgument(0));
                        return true;
                    });
            movement.when(() -> WiredUserMovementHelper.moveUser(
                            room, unit, target, 0.0, RoomUserRotation.NORTH, RoomUserRotation.NORTH, 0, true))
                    .thenAnswer(call -> {
                        unit.setCurrentLocation(target);
                        return true;
                    });
            try (var batch = WiredInternalVariableSupport.beginUserMoveBatch()) {
                assertTrue(WiredInternalVariableSupport.writeUserValue(room, unit, "@position_x", 3, 0, true));
                assertTrue(WiredInternalVariableSupport.writeUserValue(room, unit, "@position_y", 4, 0, true));
                assertTrue(events.isEmpty());
            }
            assertEquals(
                    List.of("@position_x", "@position_y"),
                    events.stream().map(WiredEvent::getInternalVariableKey).toList());
            assertEquals(
                    List.of(0L, 0L),
                    events.stream().map(WiredEvent::getOldVariableValue).toList());
            assertEquals(
                    List.of(3L, 4L),
                    events.stream().map(WiredEvent::getNewVariableValue).toList());

            try (var batch = WiredInternalVariableSupport.beginUserMoveBatch()) {
                assertTrue(WiredInternalVariableSupport.writeUserValue(room, unit, "@position_x", 7, 0, true));
            }
            assertEquals(2, events.size());
        } finally {
            WiredInternalVariableSupport.clearThreadLocalsForCurrentThread();
        }
    }

    private static WiredEvent changed(Room room, int type, String key, int previous, int current) {
        return WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                .variableTargetType(type)
                .internalVariableKey(key)
                .variableValues(previous, current)
                .variableChangeKind(WiredEvent.VariableChangeKind.INCREASED)
                .build();
    }
}
