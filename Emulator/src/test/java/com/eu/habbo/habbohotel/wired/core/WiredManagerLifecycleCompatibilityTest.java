package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.eu.habbo.habbohotel.wired.tick.WiredTickService;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WiredManagerLifecycleCompatibilityTest {

    @AfterEach
    void resetManagerState() throws ReflectiveOperationException {
        setRuntime(null);
        setStaticField("initialized", false);
        setStaticField("engine", null);
        setStaticField("stackIndex", null);
        WiredManager.setDebugEnabled(false);
        clearThreadLocal("EVENT_HANDLING_DEPTH");
        clearThreadLocal("DEFERRED_EFFECT_EVENTS");
        clearThreadLocal(WiredInternalVariableSupport.class, "USER_MOVE_INSTANT_OVERRIDE");
        clearThreadLocal(WiredInternalVariableSupport.class, "USER_MOVE_BATCH");
        clearThreadLocal(WiredInternalVariableSupport.class, "USER_MOVE_BATCH_DEPTH");
        clearThreadLocal(WiredMoveCarryHelper.class, "COLLECTED_MOVEMENTS");
        clearThreadLocal(WiredMoveCarryHelper.class, "MOVEMENT_COLLECTION_DEPTH");
        clearThreadLocal(WiredUserMovementHelper.class, "SUPPRESSED_STATUS_ROOM_UNIT_IDS");
        clearThreadLocal(WiredSelectionFilterSupport.class, "FILTER_DEPTH");
        clearThreadLocal(WiredExecutionScope.class, "CURRENT");
    }

    @Test
    void shutdownClearsOwnedReferencesAndCurrentThreadWorkScopes() throws ReflectiveOperationException {
        WiredEngine engine = mock(WiredEngine.class);
        RoomWiredStackIndex stackIndex = mock(RoomWiredStackIndex.class);
        WiredTickService tickService = mock(WiredTickService.class);
        WiredRuntime runtime = new WiredRuntime(engine, stackIndex, tickService);
        runtime.start();

        setStaticField("engine", engine);
        setStaticField("stackIndex", stackIndex);
        setStaticField("initialized", true);
        setRuntime(runtime);
        setThreadLocal("EVENT_HANDLING_DEPTH", 1);
        setThreadLocal("DEFERRED_EFFECT_EVENTS", new ArrayDeque<>());
        WiredInternalVariableSupport.beginUserMoveInstantOverride(true);
        WiredInternalVariableSupport.beginUserMoveBatch();
        WiredMoveCarryHelper.beginMovementCollection();
        setThreadLocal(WiredUserMovementHelper.class, "SUPPRESSED_STATUS_ROOM_UNIT_IDS", java.util.Set.of(88));
        setThreadLocal(WiredSelectionFilterSupport.class, "FILTER_DEPTH", 2);
        setThreadLocal(WiredExecutionScope.class, "CURRENT", mock(WiredContext.class));
        WiredManager.setDebugEnabled(true);

        WiredManager.shutdown();

        assertFalse(WiredManager.isEnabled());
        assertFalse(WiredManager.isDebugEnabled());
        assertNull(WiredManager.getEngine());
        assertNull(WiredManager.getStackIndex());
        assertNull(runtimeReference().get());
        assertNull(threadLocalValue(WiredManager.class, "EVENT_HANDLING_DEPTH"));
        assertNull(threadLocalValue(WiredManager.class, "DEFERRED_EFFECT_EVENTS"));
        assertNull(threadLocalValue(WiredInternalVariableSupport.class, "USER_MOVE_INSTANT_OVERRIDE"));
        assertNull(threadLocalValue(WiredInternalVariableSupport.class, "USER_MOVE_BATCH"));
        assertNull(threadLocalValue(WiredInternalVariableSupport.class, "USER_MOVE_BATCH_DEPTH"));
        assertNull(threadLocalValue(WiredMoveCarryHelper.class, "COLLECTED_MOVEMENTS"));
        assertNull(threadLocalValue(WiredMoveCarryHelper.class, "MOVEMENT_COLLECTION_DEPTH"));
        assertNull(threadLocalValue(WiredUserMovementHelper.class, "SUPPRESSED_STATUS_ROOM_UNIT_IDS"));
        assertEquals(0, threadLocalValue(WiredSelectionFilterSupport.class, "FILTER_DEPTH"));
        assertNull(threadLocalValue(WiredExecutionScope.class, "CURRENT"));
        verify(tickService).stop();
        verify(engine).shutdownScheduledWork();
        verify(stackIndex).clearAll();
        verify(engine).clearUnseenCache();
        verify(engine).clearAllDiagnostics();
        verify(engine).clearAllExecutionCaches();
    }

    @Test
    void shutdownRepairsPartiallyPublishedStateEvenWhenNotMarkedInitialized() throws ReflectiveOperationException {
        WiredEngine engine = mock(WiredEngine.class);
        RoomWiredStackIndex stackIndex = mock(RoomWiredStackIndex.class);
        setStaticField("engine", engine);
        setStaticField("stackIndex", stackIndex);
        setStaticField("initialized", false);

        WiredManager.shutdown();

        assertNull(WiredManager.getEngine());
        assertNull(WiredManager.getStackIndex());
        verify(engine).shutdownScheduledWork();
        verify(stackIndex).clearAll();
        verify(engine).clearAllExecutionCaches();
    }

    private static void setStaticField(String name, Object value) throws ReflectiveOperationException {
        Field field = WiredManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void setRuntime(WiredRuntime runtime) throws ReflectiveOperationException {
        runtimeReference().set(runtime);
    }

    @SuppressWarnings("unchecked")
    private static AtomicReference<WiredRuntime> runtimeReference() throws ReflectiveOperationException {
        Field field = WiredManager.class.getDeclaredField("RUNTIME");
        field.setAccessible(true);
        return (AtomicReference<WiredRuntime>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static void setThreadLocal(String name, Object value) throws ReflectiveOperationException {
        setThreadLocal(WiredManager.class, name, value);
    }

    @SuppressWarnings("unchecked")
    private static void setThreadLocal(Class<?> owner, String name, Object value) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        ((ThreadLocal<Object>) field.get(null)).set(value);
    }

    private static void clearThreadLocal(String name) throws ReflectiveOperationException {
        clearThreadLocal(WiredManager.class, name);
    }

    @SuppressWarnings("unchecked")
    private static void clearThreadLocal(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        ((ThreadLocal<Object>) field.get(null)).remove();
    }

    private static Object threadLocalValue(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return ((ThreadLocal<?>) field.get(null)).get();
    }
}
