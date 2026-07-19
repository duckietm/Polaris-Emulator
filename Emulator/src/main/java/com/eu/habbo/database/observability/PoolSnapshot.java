package com.eu.habbo.database.observability;

public record PoolSnapshot(int active, int idle, int total, int awaiting) {
    public static final PoolSnapshot UNAVAILABLE = new PoolSnapshot(-1, -1, -1, -1);
}
