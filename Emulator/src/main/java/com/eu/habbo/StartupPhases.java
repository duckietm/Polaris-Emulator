package com.eu.habbo;

import java.util.List;
import java.util.Objects;

/**
 * Runs bootstrap phases in their declared order and supports intentional
 * short-circuit modes such as migration validation.
 */
final class StartupPhases {

    private StartupPhases() {}

    static boolean run(List<Phase> phases) throws Exception {
        for (Phase phase : phases) {
            if (!phase.action().run()) {
                return false;
            }
        }
        return true;
    }

    record Phase(String name, Action action) {
        Phase {
            Objects.requireNonNull(name);
            Objects.requireNonNull(action);
        }
    }

    @FunctionalInterface
    interface Action {
        boolean run() throws Exception;
    }
}
