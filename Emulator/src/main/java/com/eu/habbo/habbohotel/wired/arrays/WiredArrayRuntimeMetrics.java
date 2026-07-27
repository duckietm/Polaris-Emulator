package com.eu.habbo.habbohotel.wired.arrays;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Process-wide array load, cache, rejection and persistence diagnostics. */
public final class WiredArrayRuntimeMetrics {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredArrayRuntimeMetrics.class);
    private static final LongAdder GUARDED_MUTATIONS = new LongAdder();
    private static final LongAdder REJECTED_MUTATIONS = new LongAdder();
    private static final LongAdder RESOLVED_OWNERS = new LongAdder();
    private static final LongAdder COPIED_ENTRIES = new LongAdder();
    private static final LongAdder ESTIMATED_PERSISTENT_ROWS = new LongAdder();
    private static final LongAdder PERSISTENCE_TRANSACTIONS = new LongAdder();
    private static final LongAdder PERSISTENCE_FAILURES = new LongAdder();
    private static final LongAdder PERSISTED_OWNERS = new LongAdder();
    private static final LongAdder DELETED_ROWS = new LongAdder();
    private static final LongAdder UPSERTED_ROWS = new LongAdder();
    private static final LongAdder PERSISTENCE_NANOS = new LongAdder();
    private static final AtomicLong MAX_PERSISTENCE_NANOS = new AtomicLong();
    private static final LongAdder CACHE_HITS = new LongAdder();
    private static final LongAdder CACHE_MISSES = new LongAdder();
    private static final LongAdder CACHE_EVICTIONS = new LongAdder();
    private static final AtomicLong LAST_LOGGED_AT = new AtomicLong();

    private WiredArrayRuntimeMetrics() {}

    public static void recordGuard(int ownerCount, long copiedEntries, long persistentRows, boolean accepted) {
        GUARDED_MUTATIONS.increment();
        if (!accepted) REJECTED_MUTATIONS.increment();
        RESOLVED_OWNERS.add(Math.max(0, ownerCount));
        COPIED_ENTRIES.add(Math.max(0L, copiedEntries));
        ESTIMATED_PERSISTENT_ROWS.add(Math.max(0L, persistentRows));
    }

    public static void recordPersistence(
            int ownerCount, int deletedRows, int upsertedRows, long elapsedNanos, boolean succeeded) {
        PERSISTENCE_TRANSACTIONS.increment();
        if (!succeeded) PERSISTENCE_FAILURES.increment();
        PERSISTED_OWNERS.add(Math.max(0, ownerCount));
        DELETED_ROWS.add(Math.max(0, deletedRows));
        UPSERTED_ROWS.add(Math.max(0, upsertedRows));
        long safeElapsed = Math.max(0L, elapsedNanos);
        PERSISTENCE_NANOS.add(safeElapsed);
        MAX_PERSISTENCE_NANOS.accumulateAndGet(safeElapsed, Math::max);
        maybeLogSummary();
    }

    public static void recordCacheHit() {
        CACHE_HITS.increment();
    }

    public static void recordCacheMiss() {
        CACHE_MISSES.increment();
    }

    public static void recordCacheEviction(long count) {
        CACHE_EVICTIONS.add(Math.max(0L, count));
    }

    public static Snapshot snapshot() {
        return new Snapshot(
                GUARDED_MUTATIONS.sum(),
                REJECTED_MUTATIONS.sum(),
                RESOLVED_OWNERS.sum(),
                COPIED_ENTRIES.sum(),
                ESTIMATED_PERSISTENT_ROWS.sum(),
                PERSISTENCE_TRANSACTIONS.sum(),
                PERSISTENCE_FAILURES.sum(),
                PERSISTED_OWNERS.sum(),
                DELETED_ROWS.sum(),
                UPSERTED_ROWS.sum(),
                PERSISTENCE_NANOS.sum(),
                MAX_PERSISTENCE_NANOS.get(),
                CACHE_HITS.sum(),
                CACHE_MISSES.sum(),
                CACHE_EVICTIONS.sum());
    }

    private static void maybeLogSummary() {
        long now = System.currentTimeMillis();
        long interval = WiredArraySettings.metricsLogIntervalMs();
        long previous = LAST_LOGGED_AT.get();
        if (now - previous < interval || !LAST_LOGGED_AT.compareAndSet(previous, now)) return;
        Snapshot value = snapshot();
        LOGGER.info(
                "Wired array metrics: guardedMutations={}, rejectedMutations={}, resolvedOwners={}, copiedEntries={}, estimatedPersistentRows={}, persistenceTransactions={}, persistenceFailures={}, persistedOwners={}, deletedRows={}, upsertedRows={}, persistenceTotalMs={}, persistenceMaxMs={}, cacheHits={}, cacheMisses={}, cacheEvictions={}",
                value.guardedMutations(),
                value.rejectedMutations(),
                value.resolvedOwners(),
                value.copiedEntries(),
                value.estimatedPersistentRows(),
                value.persistenceTransactions(),
                value.persistenceFailures(),
                value.persistedOwners(),
                value.deletedRows(),
                value.upsertedRows(),
                value.persistenceNanos() / 1_000_000L,
                value.maximumPersistenceNanos() / 1_000_000L,
                value.cacheHits(),
                value.cacheMisses(),
                value.cacheEvictions());
    }

    public record Snapshot(
            long guardedMutations,
            long rejectedMutations,
            long resolvedOwners,
            long copiedEntries,
            long estimatedPersistentRows,
            long persistenceTransactions,
            long persistenceFailures,
            long persistedOwners,
            long deletedRows,
            long upsertedRows,
            long persistenceNanos,
            long maximumPersistenceNanos,
            long cacheHits,
            long cacheMisses,
            long cacheEvictions) {}
}
