package com.eu.habbo.habbohotel.items;

import java.util.concurrent.locks.ReentrantLock;

/**
 * One process-wide lock serializing every furnidata reindex and every editor-driven
 * furnidata write, so an editor write never races the file watcher's reindex and the
 * volatile index is never observed mid-swap by two writers.
 */
public final class FurnidataLock {
    public static final ReentrantLock LOCK = new ReentrantLock();
    private FurnidataLock() {}
}
