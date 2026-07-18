package com.eu.habbo.database.observability;

public record SlowQueryEvent(
        long durationMs,
        String operation,
        boolean success,
        String sqlState,
        int vendorCode,
        String fingerprint,
        String sql,
        String threadName,
        PoolSnapshot pool) {
}
