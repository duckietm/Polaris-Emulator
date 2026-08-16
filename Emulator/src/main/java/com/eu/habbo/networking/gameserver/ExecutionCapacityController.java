package com.eu.habbo.networking.gameserver;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class ExecutionCapacityController {
    enum Admission {
        ADMITTED,
        SATURATED
    }

    enum Mode {
        OBSERVE,
        ENFORCE;

        static Mode parse(String value) {
            return "enforce".equalsIgnoreCase(value) ? ENFORCE : OBSERVE;
        }
    }

    record Snapshot(
            Mode mode,
            int capacity,
            int lowWatermark,
            int inFlight,
            int highWatermark,
            long wouldThrottle,
            long rejections,
            int waitingConnections) {}

    private final int capacity;
    private final int lowWatermark;
    private final Mode mode;
    private final Map<Object, Runnable> waiters = new LinkedHashMap<>();
    private final Set<Object> reservations = new HashSet<>();

    private int inFlight;
    private int highWatermark;
    private long wouldThrottle;
    private long rejections;

    ExecutionCapacityController(int capacity, int lowWatermark, Mode mode) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Execution capacity must be positive");
        }
        if (lowWatermark < 0 || lowWatermark >= capacity) {
            throw new IllegalArgumentException("Low watermark must be between zero and capacity - 1");
        }
        this.capacity = capacity;
        this.lowWatermark = lowWatermark;
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    synchronized Admission tryAcquire() {
        return tryAcquire(null);
    }

    boolean isEnforcing() {
        return this.mode == Mode.ENFORCE;
    }

    synchronized Admission tryAcquire(Object key) {
        if (key != null && this.reservations.remove(key)) {
            this.inFlight++;
            this.highWatermark = Math.max(this.highWatermark, this.inFlight);
            return Admission.ADMITTED;
        }

        if (this.mode == Mode.ENFORCE && occupiedCapacity() >= this.capacity) {
            this.rejections++;
            return Admission.SATURATED;
        }

        this.inFlight++;
        this.highWatermark = Math.max(this.highWatermark, this.inFlight);
        if (this.mode == Mode.OBSERVE && this.inFlight > this.capacity) {
            this.wouldThrottle++;
        }
        return Admission.ADMITTED;
    }

    void release() {
        Runnable waiter;
        synchronized (this) {
            if (this.inFlight == 0) {
                throw new IllegalStateException("Execution capacity released without an admission");
            }
            this.inFlight--;
            waiter = reserveNextWaiter();
        }
        if (waiter != null) {
            waiter.run();
        }
    }

    void registerWaiter(Object key, Runnable callback) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(callback, "callback");
        Runnable immediate = null;
        synchronized (this) {
            if (this.reservations.contains(key) || this.waiters.containsKey(key)) {
                return;
            }
            if (this.mode == Mode.OBSERVE) {
                immediate = callback;
            } else if (this.inFlight <= this.lowWatermark && occupiedCapacity() < this.capacity) {
                this.reservations.add(key);
                immediate = callback;
            } else {
                this.waiters.put(key, callback);
            }
        }
        if (immediate != null) {
            immediate.run();
        }
    }

    void cancelWaiter(Object key) {
        Runnable waiter;
        synchronized (this) {
            this.waiters.remove(key);
            this.reservations.remove(key);
            waiter = reserveNextWaiter();
        }
        if (waiter != null) {
            waiter.run();
        }
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(
                this.mode,
                this.capacity,
                this.lowWatermark,
                this.inFlight,
                this.highWatermark,
                this.wouldThrottle,
                this.rejections,
                this.waiters.size() + this.reservations.size());
    }

    private int occupiedCapacity() {
        return this.inFlight + this.reservations.size();
    }

    private Runnable reserveNextWaiter() {
        if (this.mode != Mode.ENFORCE
                || this.inFlight > this.lowWatermark
                || occupiedCapacity() >= this.capacity
                || this.waiters.isEmpty()) {
            return null;
        }
        Map.Entry<Object, Runnable> entry = this.waiters.entrySet().iterator().next();
        this.waiters.remove(entry.getKey());
        this.reservations.add(entry.getKey());
        return entry.getValue();
    }
}
