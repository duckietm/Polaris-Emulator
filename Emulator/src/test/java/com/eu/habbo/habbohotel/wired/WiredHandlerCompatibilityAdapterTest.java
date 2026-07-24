package com.eu.habbo.habbohotel.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.wired.core.RoomWiredStackIndex;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEngine;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredRoomDiagnostics;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.plugin.PluginManager;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WiredHandlerCompatibilityAdapterTest {
    private Object originalConfig;
    private Object originalPluginManager;
    private Object originalEngine;
    private Object originalIndex;
    private Object originalInitialized;
    private Object originalRuntime;
    private boolean originalReady;

    @BeforeEach
    void installSingleRuntimeFixture() throws ReflectiveOperationException {
        originalConfig = replaceStatic(Emulator.class, "config", mock(ConfigurationManager.class));
        PluginManager pluginManager = mock(PluginManager.class);
        doAnswer(invocation -> invocation.getArgument(0)).when(pluginManager).fireEvent(any());
        originalPluginManager = replaceStatic(Emulator.class, "pluginManager", pluginManager);
        originalReady = Emulator.isReady;
        Emulator.isReady = true;

        originalEngine = staticField(WiredManager.class, "engine").get(null);
        originalIndex = staticField(WiredManager.class, "stackIndex").get(null);
        originalInitialized = staticField(WiredManager.class, "initialized").get(null);
        AtomicReference<Object> runtime = runtimeReference();
        originalRuntime = runtime.getAndSet(null);
        WiredLegacyUsageTelemetry.resetForTests();
    }

    @AfterEach
    void restoreGlobalState() throws ReflectiveOperationException {
        replaceStatic(Emulator.class, "config", originalConfig);
        replaceStatic(Emulator.class, "pluginManager", originalPluginManager);
        Emulator.isReady = originalReady;
        replaceStatic(WiredManager.class, "engine", originalEngine);
        replaceStatic(WiredManager.class, "stackIndex", originalIndex);
        replaceStatic(WiredManager.class, "initialized", originalInitialized);
        runtimeReference().set(originalRuntime);
        WiredLegacyUsageTelemetry.resetForTests();
    }

    @Test
    void triggerTypeFacadeExecutesThroughModernRuntimeAndAccountsExactlyOnce() throws ReflectiveOperationException {
        AtomicInteger executions = new AtomicInteger();
        Room room = roomWithTwoStacks(executions);
        installRuntime(room);

        assertTrue(WiredHandler.handle(WiredTriggerType.STATE_CHANGED, null, room, new Object[0]));
        assertEquals(2, executions.get());
        assertEquals(1, WiredLegacyUsageTelemetry.count(WiredLegacyUsageTelemetry.Operation.HANDLE_TRIGGER_TYPE));

        WiredRoomDiagnostics.Snapshot diagnostics = WiredManager.getDiagnosticsSnapshot(room.getId());
        assertTrue(diagnostics.getUsageCurrentWindow() > 0);
    }

    @Test
    void directTriggerFacadeExecutesOnlyTheRequestedSourceStack() throws ReflectiveOperationException {
        AtomicInteger executions = new AtomicInteger();
        Room room = roomWithTwoStacks(executions);
        installRuntime(room);
        InteractionWiredTrigger selected = room.getRoomSpecialTypes()
                .getTriggers(WiredTriggerType.STATE_CHANGED)
                .iterator()
                .next();

        assertTrue(WiredHandler.handle(selected, null, room, new Object[0]));
        assertEquals(1, executions.get());
    }

    @Test
    void customSentinelAndDisabledRuntimeKeepLegacyFalseResult() throws ReflectiveOperationException {
        AtomicInteger executions = new AtomicInteger();
        Room room = roomWithTwoStacks(executions);
        installRuntime(room);

        assertFalse(WiredHandler.handle(WiredTriggerType.CUSTOM, null, room, new Object[0]));
        replaceStatic(WiredManager.class, "initialized", false);
        assertFalse(WiredHandler.handle(WiredTriggerType.STATE_CHANGED, null, room, new Object[0]));
        assertEquals(0, executions.get());
    }

    @Test
    void customTriggerFacadeRetainsExactClassFiltering() throws ReflectiveOperationException {
        AtomicInteger firstExecutions = new AtomicInteger();
        AtomicInteger secondExecutions = new AtomicInteger();
        Room room = customRoom(firstExecutions, secondExecutions);
        installRuntime(room);
        InteractionWiredTrigger firstTrigger = room.getRoomSpecialTypes().getTriggers(WiredTriggerType.CUSTOM).stream()
                .filter(CustomTriggerA.class::isInstance)
                .findFirst()
                .orElseThrow();

        assertTrue(WiredHandler.handleCustomTrigger(exactClass(firstTrigger), null, room, new Object[0]));
        assertEquals(1, firstExecutions.get());
        assertEquals(0, secondExecutions.get());
    }

    @Test
    void publicFacadeOwnsOnlyItsTwoReleasedCompatibilityFields() {
        assertEquals(
                Set.of("MAXIMUM_FURNI_SELECTION", "TELEPORT_DELAY"),
                Arrays.stream(WiredHandler.class.getDeclaredFields())
                        .filter(field -> Modifier.isStatic(field.getModifiers()))
                        .map(Field::getName)
                        .collect(Collectors.toSet()));
    }

    private static Room roomWithTwoStacks(AtomicInteger executions) {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        InteractionWiredTrigger firstTrigger = trigger(7001, (short) 2, (short) 3);
        InteractionWiredTrigger secondTrigger = trigger(7002, (short) 5, (short) 6);
        InteractionWiredEffect firstEffect = effect(executions);
        InteractionWiredEffect secondEffect = effect(executions);

        when(room.getId()).thenReturn(81);
        when(room.isLoaded()).thenReturn(true);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(room.getFloorItems()).thenReturn(Set.of());
        when(specialTypes.getTriggers(WiredTriggerType.STATE_CHANGED)).thenReturn(Set.of(firstTrigger, secondTrigger));
        when(specialTypes.getConditions((short) 2, (short) 3)).thenReturn(Set.of());
        when(specialTypes.getConditions((short) 5, (short) 6)).thenReturn(Set.of());
        when(specialTypes.getEffects((short) 2, (short) 3)).thenReturn(Set.of(firstEffect));
        when(specialTypes.getEffects((short) 5, (short) 6)).thenReturn(Set.of(secondEffect));
        when(specialTypes.getExtras((short) 2, (short) 3)).thenReturn(Set.of());
        when(specialTypes.getExtras((short) 5, (short) 6)).thenReturn(Set.of());
        return room;
    }

    private static InteractionWiredTrigger trigger(int id, short x, short y) {
        InteractionWiredTrigger trigger = mock(InteractionWiredTrigger.class);
        when(trigger.getId()).thenReturn(id);
        when(trigger.getX()).thenReturn(x);
        when(trigger.getY()).thenReturn(y);
        when(trigger.getType()).thenReturn(WiredTriggerType.STATE_CHANGED);
        when(trigger.matches(any(), any())).thenReturn(true);
        when(trigger.requiresActor()).thenReturn(false);
        return trigger;
    }

    private static InteractionWiredEffect effect(AtomicInteger executions) {
        InteractionWiredEffect effect = mock(InteractionWiredEffect.class);
        when(effect.getDelay()).thenReturn(0);
        when(effect.requiresActor()).thenReturn(false);
        when(effect.isSelector()).thenReturn(false);
        when(effect.canExecute(anyLong())).thenReturn(true);
        doAnswer(invocation -> {
                    executions.incrementAndGet();
                    return null;
                })
                .when(effect)
                .execute(any(WiredContext.class));
        return effect;
    }

    private static Room customRoom(AtomicInteger firstExecutions, AtomicInteger secondExecutions) {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        CustomTriggerA firstTrigger = mock(CustomTriggerA.class);
        CustomTriggerB secondTrigger = mock(CustomTriggerB.class);
        InteractionWiredEffect firstEffect = effect(firstExecutions);
        InteractionWiredEffect secondEffect = effect(secondExecutions);
        stubCustomTrigger(firstTrigger, 7101, (short) 7, (short) 8);
        stubCustomTrigger(secondTrigger, 7102, (short) 9, (short) 10);

        when(room.getId()).thenReturn(82);
        when(room.isLoaded()).thenReturn(true);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(room.getFloorItems()).thenReturn(Set.of());
        when(specialTypes.getTriggers(WiredTriggerType.CUSTOM)).thenReturn(Set.of(firstTrigger, secondTrigger));
        when(specialTypes.getConditions((short) 7, (short) 8)).thenReturn(Set.of());
        when(specialTypes.getConditions((short) 9, (short) 10)).thenReturn(Set.of());
        when(specialTypes.getEffects((short) 7, (short) 8)).thenReturn(Set.of(firstEffect));
        when(specialTypes.getEffects((short) 9, (short) 10)).thenReturn(Set.of(secondEffect));
        when(specialTypes.getExtras((short) 7, (short) 8)).thenReturn(Set.of());
        when(specialTypes.getExtras((short) 9, (short) 10)).thenReturn(Set.of());
        return room;
    }

    private static void stubCustomTrigger(InteractionWiredTrigger trigger, int id, short x, short y) {
        when(trigger.getId()).thenReturn(id);
        when(trigger.getX()).thenReturn(x);
        when(trigger.getY()).thenReturn(y);
        when(trigger.getType()).thenReturn(WiredTriggerType.CUSTOM);
        when(trigger.matches(any(), any())).thenReturn(true);
        when(trigger.requiresActor()).thenReturn(false);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends InteractionWiredTrigger> exactClass(InteractionWiredTrigger trigger) {
        return (Class<? extends InteractionWiredTrigger>) trigger.getClass();
    }

    private static void installRuntime(Room room) throws ReflectiveOperationException {
        RoomWiredStackIndex index = new RoomWiredStackIndex(false);
        WiredEngine engine = new WiredEngine(mock(WiredServices.class), index, 100);
        replaceStatic(WiredManager.class, "engine", engine);
        replaceStatic(WiredManager.class, "stackIndex", index);
        replaceStatic(WiredManager.class, "initialized", true);
    }

    @SuppressWarnings("unchecked")
    private static AtomicReference<Object> runtimeReference() throws ReflectiveOperationException {
        return (AtomicReference<Object>)
                staticField(WiredManager.class, "RUNTIME").get(null);
    }

    private static Object replaceStatic(Class<?> type, String name, Object value) throws ReflectiveOperationException {
        Field field = staticField(type, name);
        Object original = field.get(null);
        field.set(null, value);
        return original;
    }

    private static Field staticField(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private abstract static class CustomTriggerA extends InteractionWiredTrigger {
        private CustomTriggerA() {
            super(0, 0, null, "", 0, 0);
        }
    }

    private abstract static class CustomTriggerB extends InteractionWiredTrigger {
        private CustomTriggerB() {
            super(0, 0, null, "", 0, 0);
        }
    }
}
