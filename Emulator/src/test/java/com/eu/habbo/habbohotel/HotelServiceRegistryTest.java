package com.eu.habbo.habbohotel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HotelServiceRegistryTest {

    @Test
    void ownsConstructionAndDisposesInReverseOrderOnce() throws Exception {
        List<String> calls = new ArrayList<>();
        HotelServiceRegistry registry = new HotelServiceRegistry();
        Object first = new Object();
        Object second = new Object();

        assertSame(first, registry.create("first", () -> first, ignored -> calls.add("first.dispose")));
        assertSame(second, registry.create("second", () -> second, ignored -> calls.add("second.dispose")));
        registry.beforeDispose("quiesce", () -> calls.add("quiesce"));

        registry.dispose();
        registry.dispose();

        assertEquals(List.of("quiesce", "second.dispose", "first.dispose"), calls);
    }

    @Test
    void failedLifecycleActionDoesNotPreventEarlierResourceDisposal() throws Exception {
        List<String> calls = new ArrayList<>();
        HotelServiceRegistry registry = new HotelServiceRegistry();
        registry.create("first", Object::new, ignored -> calls.add("first.dispose"));
        registry.create("failing", Object::new, ignored -> {
            calls.add("failing.dispose");
            throw new IllegalStateException("expected");
        });

        registry.dispose();

        assertEquals(List.of("failing.dispose", "first.dispose"), calls);
    }
}
