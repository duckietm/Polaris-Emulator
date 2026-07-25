package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.api.WiredDelayedSnapshotProvider;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredDelayedExecutionSnapshotTest {

    @Test
    void snapshotDoesNotRetainTheCapturedRoom() {
        assertFalse(Arrays.stream(WiredDelayedExecutionSnapshot.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == Room.class));
    }

    @Test
    void rehydratesCompleteMetadataWithoutSharingMutableExecutionState() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(81);
        when(room.isLoaded()).thenReturn(true);
        when(room.getLifecycleGeneration()).thenReturn(101L);

        RoomUnit actor = unit(301);
        RoomUnit origin = unit(302);
        RoomUnit target = unit(303);
        when(room.getRoomUnits()).thenReturn(Set.of(actor, origin, target));

        HabboItem trigger = item(701);
        HabboItem source = item(702);
        Map<Integer, HabboItem> liveItems = new HashMap<>(Map.of(701, trigger, 702, source));
        Map<Integer, Long> incarnations = new HashMap<>(Map.of(701, 11L, 702, 12L));
        when(room.getHabboItem(anyInt())).thenAnswer(invocation -> liveItems.get(invocation.getArgument(0)));
        when(room.getItemIncarnation(anyInt()))
                .thenAnswer(invocation -> incarnations.getOrDefault(invocation.getArgument(0), 0L));

        WiredContextVariableScope eventVariables = new WiredContextVariableScope();
        eventVariables.assignValue(801, 5, true);
        RoomTile tile = new RoomTile((short) 3, (short) 4, (short) 1, RoomTileState.OPEN, true);
        WiredEvent event = WiredEvent.builder(WiredEvent.Type.SIGNAL_RECEIVED, room)
                .actor(actor)
                .originActor(origin)
                .sourceItem(source)
                .tile(tile)
                .text("immutable")
                .targetUnit(target)
                .score(17)
                .scoreAdded(2)
                .triggeredByEffect(true)
                .callStackDepth(4)
                .signalChannel(6)
                .actionId(8)
                .actionParameter(9)
                .chatType(10)
                .chatStyle(11)
                .signalUserCount(12)
                .signalFurniCount(13)
                .variableTargetType(14)
                .variableDefinitionItemId(801)
                .variableCreated(true)
                .variableDeleted(false)
                .variableChangeKind(WiredEvent.VariableChangeKind.INCREASED)
                .contextVariableScope(eventVariables)
                .createdAtMs(123_456L)
                .build();
        Object opaquePluginValue = new Object();
        WiredState state = new WiredState(50);
        state.step();
        state.step();
        WiredContext context = new WiredContext(event, trigger, null, mock(WiredServices.class), state, new Object[] {
            source, new Object[] {actor, "nested"}, new int[] {1, 2}, opaquePluginValue
        });
        context.targets().setUsers(List.of(actor, target));
        context.targets().setItems(List.of(source));
        context.setIncludeWiredSelectorItems(true);
        context.contextVariables().assignValue(802, 7, true);
        IWiredEffect programmaticEffect = ignored -> {};

        WiredDelayedExecutionSnapshot snapshot =
                WiredDelayedExecutionSnapshot.capture(List.of(programmaticEffect), context, 120_000L);

        context.targets().clear();
        context.contextVariables().updateValue(802, 99);
        state.step();
        WiredDelayedExecutionSnapshot.Resolved resolved = snapshot.resolve(room).orElseThrow();
        WiredContext delayed = resolved.context();

        assertSame(programmaticEffect, resolved.effects().getFirst());
        assertSame(trigger, delayed.triggerItem());
        assertSame(actor, delayed.actor().orElseThrow());
        assertSame(origin, delayed.event().getOriginActor().orElseThrow());
        assertSame(source, delayed.sourceItem().orElseThrow());
        assertSame(target, delayed.event().getTargetUnit().orElseThrow());
        assertEquals(3, delayed.tile().orElseThrow().x);
        assertEquals(4, delayed.tile().orElseThrow().y);
        assertEquals("immutable", delayed.text().orElseThrow());
        assertEquals(17, delayed.event().getScore());
        assertEquals(2, delayed.event().getScoreAdded());
        assertTrue(delayed.event().isTriggeredByEffect());
        assertEquals(4, delayed.event().getCallStackDepth());
        assertEquals(6, delayed.event().getSignalChannel());
        assertEquals(8, delayed.event().getActionId());
        assertEquals(9, delayed.event().getActionParameter());
        assertEquals(10, delayed.event().getChatType());
        assertEquals(11, delayed.event().getChatStyle());
        assertEquals(12, delayed.event().getSignalUserCount());
        assertEquals(13, delayed.event().getSignalFurniCount());
        assertEquals(14, delayed.event().getVariableTargetType());
        assertEquals(801, delayed.event().getVariableDefinitionItemId());
        assertTrue(delayed.event().isVariableCreated());
        assertFalse(delayed.event().isVariableDeleted());
        assertEquals(WiredEvent.VariableChangeKind.INCREASED, delayed.event().getVariableChangeKind());
        assertEquals(123_456L, delayed.event().getCreatedAtMs());
        assertEquals(5, delayed.event().getContextVariableScope().getValue(801));
        assertEquals(Set.of(actor, target), delayed.targets().users());
        assertEquals(Set.of(source), delayed.targets().items());
        assertTrue(delayed.targets().isUsersModifiedBySelector());
        assertTrue(delayed.targets().isItemsModifiedBySelector());
        assertTrue(delayed.includeWiredSelectorItems());
        assertEquals(7, delayed.contextVariables().getValue(802));
        assertEquals(2, delayed.state().steps());
        assertEquals(state.runId(), delayed.state().runId());
        assertSame(source, delayed.legacySettings()[0]);
        assertSame(actor, ((Object[]) delayed.legacySettings()[1])[0]);
        assertArrayEquals(new int[] {1, 2}, (int[]) delayed.legacySettings()[2]);
        assertSame(opaquePluginValue, delayed.legacySettings()[3]);
    }

    @Test
    void omitsASelectedItemWhoseIdNowBelongsToAnotherIncarnation() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(82);
        when(room.isLoaded()).thenReturn(true);
        when(room.getLifecycleGeneration()).thenReturn(102L);
        when(room.getRoomUnits()).thenReturn(Set.of());
        HabboItem selected = item(711);
        HabboItem replacement = item(711);
        var liveItem = new java.util.concurrent.atomic.AtomicReference<>(selected);
        var incarnation = new java.util.concurrent.atomic.AtomicLong(21L);
        when(room.getHabboItem(711)).thenAnswer(ignored -> liveItem.get());
        when(room.getItemIncarnation(711)).thenAnswer(ignored -> incarnation.get());

        WiredContext context = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(10));
        context.targets().setItems(List.of(selected));
        WiredDelayedExecutionSnapshot snapshot =
                WiredDelayedExecutionSnapshot.capture(List.of((IWiredEffect) ignored -> {}), context, 1_000L);

        liveItem.set(replacement);
        incarnation.incrementAndGet();
        WiredContext resolved = snapshot.resolve(room).orElseThrow().context();

        assertTrue(resolved.targets().items().isEmpty());
        assertTrue(resolved.targets().isItemsModifiedBySelector());
    }

    @Test
    void usesDetachedPluginSnapshotsForProgrammaticComponentsAndLegacyValues() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(83);
        when(room.isLoaded()).thenReturn(true);
        when(room.getLifecycleGeneration()).thenReturn(103L);
        when(room.getRoomUnits()).thenReturn(Set.of());

        SnapshottingPluginValue pluginValue = new SnapshottingPluginValue(List.of("scheduled"));
        SnapshottingPluginEffect pluginEffect = new SnapshottingPluginEffect("scheduled-effect");
        WiredContext context = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(),
                null,
                mock(WiredServices.class),
                new WiredState(10),
                new Object[] {pluginValue});

        WiredDelayedExecutionSnapshot snapshot =
                WiredDelayedExecutionSnapshot.capture(List.of(pluginEffect), context, 2_000L);
        pluginValue.values.add("late-mutation");
        pluginEffect.label = "late-effect-mutation";

        WiredDelayedExecutionSnapshot.Resolved resolved = snapshot.resolve(room).orElseThrow();
        SnapshottingPluginValue delayedValue =
                (SnapshottingPluginValue) resolved.context().legacySettings()[0];
        SnapshottingPluginEffect delayedEffect =
                (SnapshottingPluginEffect) resolved.effects().getFirst();

        assertNotSame(pluginValue, delayedValue);
        assertEquals(List.of("scheduled"), delayedValue.values);
        assertNotSame(pluginEffect, delayedEffect);
        assertEquals("scheduled-effect", delayedEffect.label);
    }

    private static final class SnapshottingPluginValue
            implements WiredDelayedSnapshotProvider<SnapshottingPluginValue> {
        private final List<String> values;

        private SnapshottingPluginValue(List<String> values) {
            this.values = new java.util.ArrayList<>(values);
        }

        @Override
        public SnapshottingPluginValue snapshotForDelayedExecution() {
            return new SnapshottingPluginValue(this.values);
        }
    }

    private static final class SnapshottingPluginEffect
            implements IWiredEffect, WiredDelayedSnapshotProvider<SnapshottingPluginEffect> {
        private String label;

        private SnapshottingPluginEffect(String label) {
            this.label = label;
        }

        @Override
        public void execute(WiredContext ignored) {}

        @Override
        public SnapshottingPluginEffect snapshotForDelayedExecution() {
            return new SnapshottingPluginEffect(this.label);
        }
    }

    private static RoomUnit unit(int id) {
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getId()).thenReturn(id);
        return unit;
    }

    private static HabboItem item(int id) {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        return item;
    }
}
