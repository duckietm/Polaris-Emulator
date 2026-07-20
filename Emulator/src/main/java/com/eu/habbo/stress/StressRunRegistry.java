package com.eu.habbo.stress;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class StressRunRegistry {
    private static final AtomicReference<StressRunManager> MANAGER = new AtomicReference<>();

    private StressRunRegistry() {}

    public static void install(StressRunManager manager) {
        MANAGER.set(Objects.requireNonNull(manager));
    }

    public static StressRunManager get() {
        StressRunManager manager = MANAGER.get();
        if (manager == null) {
            throw new IllegalStateException("stress controls are unavailable");
        }
        return manager;
    }
}
