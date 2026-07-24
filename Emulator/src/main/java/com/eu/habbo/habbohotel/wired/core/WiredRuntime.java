package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.wired.tick.WiredTickService;

/**
 * Owns the wired engine, stack index, and tick-service lifecycle.
 *
 * <p>{@link WiredManager} remains the static compatibility facade used by legacy plugins and existing
 * first-party callers.
 */
final class WiredRuntime {
    private final WiredEngine engine;
    private final RoomWiredStackIndex stackIndex;
    private final WiredTickService tickService;
    private final WiredRuntimeLifecycle lifecycle;

    WiredRuntime(WiredEngine engine, RoomWiredStackIndex stackIndex, WiredTickService tickService) {
        this.engine = engine;
        this.stackIndex = stackIndex;
        this.tickService = tickService;
        this.lifecycle = new WiredRuntimeLifecycle(engine, stackIndex, tickService);
    }

    void start() {
        this.lifecycle.start();
    }

    void shutdown() {
        this.lifecycle.shutdown();
    }

    boolean isActive() {
        return this.lifecycle.isActive();
    }

    WiredEngine engine() {
        return engine;
    }

    RoomWiredStackIndex stackIndex() {
        return stackIndex;
    }

    WiredTickService tickService() {
        return tickService;
    }
}
