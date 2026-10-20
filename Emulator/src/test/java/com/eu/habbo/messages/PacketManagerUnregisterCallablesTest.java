package com.eu.habbo.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PacketManagerUnregisterCallablesTest {
    @Test
    void unregisteringOneHeaderLeavesTheCallablesOfTheOthers() throws Exception {
        PacketManager manager = mock(PacketManager.class, CALLS_REAL_METHODS);
        Map<Integer, List<ICallable>> callables = new HashMap<>();
        Field field = PacketManager.class.getDeclaredField("callables");
        field.setAccessible(true);
        field.set(manager, callables);
        ICallable first = mock(ICallable.class);
        ICallable second = mock(ICallable.class);
        manager.registerCallable(1, first);
        manager.registerCallable(2, second);

        manager.unregisterCallables(1);

        assertFalse(callables.containsKey(1));
        assertEquals(List.of(second), callables.get(2));
    }
}
