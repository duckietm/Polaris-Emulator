package com.eu.habbo;

import java.util.concurrent.atomic.AtomicReference;

final class ShutdownPhaseController {
    private final AtomicReference<ShutdownPhase> current = new AtomicReference<>(ShutdownPhase.RUNNING);

    ShutdownPhase current() {
        return current.get();
    }

    boolean advanceTo(ShutdownPhase next) {
        ShutdownPhase previous = current.get();
        if (next.ordinal() != previous.ordinal() + 1) {
            return false;
        }
        return current.compareAndSet(previous, next);
    }
}
