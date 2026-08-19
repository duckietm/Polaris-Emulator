package com.eu.habbo.resilience;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class RuntimeResilienceController {

    public enum Mode {
        OBSERVE,
        ENFORCE
    }

    public enum State {
        NORMAL,
        DEGRADED,
        CRITICAL,
        RECOVERING
    }

    public enum Pressure {
        HEALTHY,
        DEGRADED,
        CRITICAL
    }

    public enum WorkClass {
        ESSENTIAL,
        INTERACTIVE,
        BACKGROUND
    }

    public enum Action {
        ALLOW,
        DEFER,
        REJECT
    }

    private final Mode mode;
    private final Thresholds thresholds;
    private final AtomicLong transitions = new AtomicLong();
    private final AtomicLong wouldDefer = new AtomicLong();
    private final AtomicLong wouldReject = new AtomicLong();
    private final AtomicLong deferred = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();

    private State state = State.NORMAL;
    private int consecutiveDegraded;
    private int consecutiveCritical;
    private int consecutiveHealthy;

    public RuntimeResilienceController(Mode mode, Thresholds thresholds) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.thresholds = Objects.requireNonNull(thresholds, "thresholds");
    }

    public synchronized void record(Pressure pressure) {
        Objects.requireNonNull(pressure, "pressure");
        switch (pressure) {
            case HEALTHY -> recordHealthy();
            case DEGRADED -> recordDegraded();
            case CRITICAL -> recordCritical();
        }
    }

    public Admission admit(WorkClass workClass) {
        Objects.requireNonNull(workClass, "workClass");
        State currentState;
        synchronized (this) {
            currentState = this.state;
        }

        Action recommendedAction = recommendedAction(currentState, workClass);
        if (recommendedAction == Action.DEFER) {
            this.wouldDefer.incrementAndGet();
        } else if (recommendedAction == Action.REJECT) {
            this.wouldReject.incrementAndGet();
        }

        Action effectiveAction = this.mode == Mode.OBSERVE ? Action.ALLOW : recommendedAction;
        if (effectiveAction == Action.DEFER) {
            this.deferred.incrementAndGet();
        } else if (effectiveAction == Action.REJECT) {
            this.rejected.incrementAndGet();
        }

        return new Admission(currentState, workClass, recommendedAction, effectiveAction);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                this.mode,
                this.state,
                this.transitions.get(),
                this.wouldDefer.get(),
                this.wouldReject.get(),
                this.deferred.get(),
                this.rejected.get());
    }

    private void recordHealthy() {
        this.consecutiveDegraded = 0;
        this.consecutiveCritical = 0;
        this.consecutiveHealthy++;

        if (this.consecutiveHealthy < this.thresholds.recoveryConsecutive()) {
            return;
        }

        if (this.state == State.CRITICAL || this.state == State.DEGRADED) {
            transitionTo(State.RECOVERING);
            this.consecutiveHealthy = 0;
        } else if (this.state == State.RECOVERING) {
            transitionTo(State.NORMAL);
            this.consecutiveHealthy = 0;
        }
    }

    private void recordDegraded() {
        this.consecutiveHealthy = 0;
        this.consecutiveCritical = 0;

        if (this.state == State.RECOVERING) {
            transitionTo(State.DEGRADED);
            this.consecutiveDegraded = 0;
            return;
        }
        if (this.state == State.CRITICAL) {
            return;
        }

        this.consecutiveDegraded++;
        if (this.consecutiveDegraded >= this.thresholds.degradedConsecutive()) {
            transitionTo(State.DEGRADED);
            this.consecutiveDegraded = 0;
        }
    }

    private void recordCritical() {
        this.consecutiveHealthy = 0;
        this.consecutiveDegraded = 0;
        this.consecutiveCritical++;
        if (this.consecutiveCritical >= this.thresholds.criticalConsecutive()) {
            transitionTo(State.CRITICAL);
            this.consecutiveCritical = 0;
        }
    }

    private void transitionTo(State nextState) {
        if (this.state == nextState) {
            return;
        }
        this.state = nextState;
        this.transitions.incrementAndGet();
    }

    private static Action recommendedAction(State state, WorkClass workClass) {
        if (workClass == WorkClass.ESSENTIAL || state == State.NORMAL) {
            return Action.ALLOW;
        }
        if (workClass == WorkClass.BACKGROUND) {
            return Action.DEFER;
        }
        return state == State.CRITICAL ? Action.REJECT : Action.ALLOW;
    }

    public record Thresholds(int degradedConsecutive, int criticalConsecutive, int recoveryConsecutive) {
        public Thresholds {
            if (degradedConsecutive < 1 || criticalConsecutive < 1 || recoveryConsecutive < 1) {
                throw new IllegalArgumentException("resilience thresholds must be positive");
            }
        }
    }

    public record Admission(State state, WorkClass workClass, Action recommendedAction, Action effectiveAction) {}

    public record Snapshot(
            Mode mode,
            State state,
            long transitions,
            long wouldDefer,
            long wouldReject,
            long deferred,
            long rejected) {}
}
