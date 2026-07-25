package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.wired.tick.WiredTickService;
import java.util.Objects;

/** Internal owner of ordered WIRED runtime startup and cleanup. */
final class WiredRuntimeLifecycle {
    private final WiredEngine engine;
    private final RoomWiredStackIndex stackIndex;
    private final WiredTickService tickService;
    private boolean active;

    WiredRuntimeLifecycle(WiredEngine engine, RoomWiredStackIndex stackIndex, WiredTickService tickService) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.stackIndex = Objects.requireNonNull(stackIndex, "stackIndex");
        this.tickService = Objects.requireNonNull(tickService, "tickService");
    }

    synchronized void start() {
        if (this.active) {
            return;
        }

        this.tickService.start();
        this.active = true;
    }

    synchronized void shutdown() {
        if (!this.active) {
            return;
        }

        this.tickService.stop();
        this.engine.shutdownScheduledWork();
        this.stackIndex.clearAll();
        this.engine.clearUnseenCache();
        this.engine.clearAllDiagnostics();
        this.engine.clearAllExecutionCaches();
        this.active = false;
    }

    synchronized boolean isActive() {
        return this.active;
    }
}
