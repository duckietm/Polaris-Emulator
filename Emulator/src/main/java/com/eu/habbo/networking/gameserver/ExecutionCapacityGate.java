package com.eu.habbo.networking.gameserver;

import com.eu.habbo.monitoring.ExecutionBackpressureMetrics;
import java.util.concurrent.Semaphore;

final class ExecutionCapacityGate {
    private final Semaphore permits;
    private final ExecutionBackpressureMetrics.Lane lane;

    ExecutionCapacityGate(int capacity, ExecutionBackpressureMetrics.Lane lane) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Execution capacity must be positive");
        }
        this.permits = new Semaphore(capacity);
        this.lane = lane;
    }

    boolean tryAcquire() {
        if (this.permits.tryAcquire()) {
            return true;
        }
        ExecutionBackpressureMetrics.recordRejection(this.lane);
        return false;
    }

    void release() {
        this.permits.release();
    }
}
