package com.eu.habbo.habbohotel.wired.core;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

/**
 * Tracks wired monitor data for a single room.
 */
public final class WiredRoomDiagnostics {

    public enum Type {
        EXECUTION_CAP,
        DELAYED_EVENTS_CAP,
        EXECUTOR_OVERLOAD,
        MARKED_AS_HEAVY,
        KILLED,
        RECURSION_TIMEOUT
    }

    public enum Severity {
        WARNING,
        ERROR
    }

    public static final class LogEntry {
        private final Type type;
        private final Severity severity;
        private int count;
        private long firstOccurredAtMs;
        private long lastOccurredAtMs;
        private String latestReason;
        private String latestSourceLabel;
        private int latestSourceId;

        private LogEntry(Type type, Severity severity) {
            this.type = type;
            this.severity = severity;
        }

        private void record(long now, String reason, String sourceLabel, int sourceId) {
            if (this.count <= 0) {
                this.firstOccurredAtMs = now;
            }

            this.count++;
            this.lastOccurredAtMs = now;
            this.latestReason = sanitizeReason(reason);
            this.latestSourceLabel = sanitizeSourceLabel(sourceLabel);
            this.latestSourceId = Math.max(0, sourceId);
        }

        public Type getType() {
            return type;
        }

        public Severity getSeverity() {
            return severity;
        }

        public int getCount() {
            return count;
        }

        public long getFirstOccurredAtMs() {
            return firstOccurredAtMs;
        }

        public long getLastOccurredAtMs() {
            return lastOccurredAtMs;
        }

        public String getLatestReason() {
            return latestReason;
        }

        public String getLatestSourceLabel() {
            return latestSourceLabel;
        }

        public int getLatestSourceId() {
            return latestSourceId;
        }
    }

    public static final class Snapshot {
        private final int usageCurrentWindow;
        private final int usageLimitPerWindow;
        private final boolean heavy;
        private final int delayedEventsPending;
        private final int delayedEventsLimit;
        private final int averageExecutionMs;
        private final int peakExecutionMs;
        private final int recursionDepthCurrent;
        private final int recursionDepthLimit;
        private final int killedRemainingSeconds;
        private final int usageWindowMs;
        private final int overloadAverageThresholdMs;
        private final int overloadPeakThresholdMs;
        private final int heavyUsageThresholdPercent;
        private final int heavyConsecutiveWindowsThreshold;
        private final int overloadConsecutiveWindowsThreshold;
        private final int heavyDelayedThresholdPercent;
        private final List<LogEntry> logs;
        private final List<HistoryEntry> history;

        public Snapshot(int usageCurrentWindow, int usageLimitPerWindow, boolean heavy, int delayedEventsPending,
                        int delayedEventsLimit, int averageExecutionMs, int peakExecutionMs,
                        int recursionDepthCurrent, int recursionDepthLimit, int killedRemainingSeconds,
                        int usageWindowMs, int overloadAverageThresholdMs, int overloadPeakThresholdMs,
                        int heavyUsageThresholdPercent, int heavyConsecutiveWindowsThreshold,
                        int overloadConsecutiveWindowsThreshold, int heavyDelayedThresholdPercent,
                        List<LogEntry> logs, List<HistoryEntry> history) {
            this.usageCurrentWindow = usageCurrentWindow;
            this.usageLimitPerWindow = usageLimitPerWindow;
            this.heavy = heavy;
            this.delayedEventsPending = delayedEventsPending;
            this.delayedEventsLimit = delayedEventsLimit;
            this.averageExecutionMs = averageExecutionMs;
            this.peakExecutionMs = peakExecutionMs;
            this.recursionDepthCurrent = recursionDepthCurrent;
            this.recursionDepthLimit = recursionDepthLimit;
            this.killedRemainingSeconds = killedRemainingSeconds;
            this.usageWindowMs = usageWindowMs;
            this.overloadAverageThresholdMs = overloadAverageThresholdMs;
            this.overloadPeakThresholdMs = overloadPeakThresholdMs;
            this.heavyUsageThresholdPercent = heavyUsageThresholdPercent;
            this.heavyConsecutiveWindowsThreshold = heavyConsecutiveWindowsThreshold;
            this.overloadConsecutiveWindowsThreshold = overloadConsecutiveWindowsThreshold;
            this.heavyDelayedThresholdPercent = heavyDelayedThresholdPercent;
            this.logs = Collections.unmodifiableList(logs);
            this.history = Collections.unmodifiableList(history);
        }

        public int getUsageCurrentWindow() {
            return usageCurrentWindow;
        }

        public int getUsageLimitPerWindow() {
            return usageLimitPerWindow;
        }

        public boolean isHeavy() {
            return heavy;
        }

        public int getDelayedEventsPending() {
            return delayedEventsPending;
        }

        public int getDelayedEventsLimit() {
            return delayedEventsLimit;
        }

        public int getAverageExecutionMs() {
            return averageExecutionMs;
        }

        public int getPeakExecutionMs() {
            return peakExecutionMs;
        }

        public int getRecursionDepthCurrent() {
            return recursionDepthCurrent;
        }

        public int getRecursionDepthLimit() {
            return recursionDepthLimit;
        }

        public int getKilledRemainingSeconds() {
            return killedRemainingSeconds;
        }

        public int getUsageWindowMs() {
            return usageWindowMs;
        }

        public int getOverloadAverageThresholdMs() {
            return overloadAverageThresholdMs;
        }

        public int getOverloadPeakThresholdMs() {
            return overloadPeakThresholdMs;
        }

        public int getHeavyUsageThresholdPercent() {
            return heavyUsageThresholdPercent;
        }

        public int getHeavyConsecutiveWindowsThreshold() {
            return heavyConsecutiveWindowsThreshold;
        }

        public int getOverloadConsecutiveWindowsThreshold() {
            return overloadConsecutiveWindowsThreshold;
        }

        public int getHeavyDelayedThresholdPercent() {
            return heavyDelayedThresholdPercent;
        }

        public List<LogEntry> getLogs() {
            return logs;
        }

        public List<HistoryEntry> getHistory() {
            return history;
        }
    }

    public static final class HistoryEntry {
        private final Type type;
        private final Severity severity;
        private final long occurredAtMs;
        private final String reason;
        private final String sourceLabel;
        private final int sourceId;

        public HistoryEntry(Type type, Severity severity, long occurredAtMs, String reason, String sourceLabel, int sourceId) {
            this.type = type;
            this.severity = severity;
            this.occurredAtMs = occurredAtMs;
            this.reason = sanitizeReason(reason);
            this.sourceLabel = sanitizeSourceLabel(sourceLabel);
            this.sourceId = Math.max(0, sourceId);
        }

        public Type getType() {
            return type;
        }

        public Severity getSeverity() {
            return severity;
        }

        public long getOccurredAtMs() {
            return occurredAtMs;
        }

        public String getReason() {
            return reason;
        }

        public String getSourceLabel() {
            return sourceLabel;
        }

        public int getSourceId() {
            return sourceId;
        }
    }

    private final int usageWindowMs;
    private final int usageLimitPerWindow;
    private final int delayedEventsLimit;
    private final int overloadAverageThresholdMs;
    private final int overloadPeakThresholdMs;
    private final int heavyUsageThresholdPercent;
    private final int heavyConsecutiveWindowsThreshold;
    private final int overloadConsecutiveWindowsThreshold;
    private final int heavyDelayedThresholdPercent;
    private final EnumMap<Type, LogEntry> logs;
    private final ArrayDeque<HistoryEntry> history;
    private final int maxHistoryEntries;

    private final java.util.concurrent.atomic.AtomicLong windowStartedAt = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicInteger usageCurrentWindow = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger delayedEventsPending = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicLong totalExecutionMsCurrentWindow = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicInteger executionSamplesCurrentWindow = new java.util.concurrent.atomic.AtomicInteger();
    private volatile int averageExecutionMs;
    private volatile int peakExecutionMs;
    private volatile int consecutiveHeavyWindows;
    private volatile int consecutiveOverloadWindows;
    private volatile boolean heavy;
    private volatile String peakExecutionSourceLabel;
    private volatile int peakExecutionSourceId;
    private volatile String peakExecutionReason;

    public WiredRoomDiagnostics(int usageWindowMs, int usageLimitPerWindow, int delayedEventsLimit,
                                int overloadAverageThresholdMs, int overloadPeakThresholdMs,
                                int heavyUsageThresholdPercent, int heavyConsecutiveWindowsThreshold) {
        this(usageWindowMs, usageLimitPerWindow, delayedEventsLimit, overloadAverageThresholdMs, overloadPeakThresholdMs,
                heavyUsageThresholdPercent, heavyConsecutiveWindowsThreshold, 2, 60, 200);
    }

    public WiredRoomDiagnostics(int usageWindowMs, int usageLimitPerWindow, int delayedEventsLimit,
                                int overloadAverageThresholdMs, int overloadPeakThresholdMs,
                                int heavyUsageThresholdPercent, int heavyConsecutiveWindowsThreshold,
                                int overloadConsecutiveWindowsThreshold, int heavyDelayedThresholdPercent,
                                int maxHistoryEntries) {
        this.usageWindowMs = Math.max(250, usageWindowMs);
        this.usageLimitPerWindow = Math.max(1, usageLimitPerWindow);
        this.delayedEventsLimit = Math.max(1, delayedEventsLimit);
        this.overloadAverageThresholdMs = Math.max(1, overloadAverageThresholdMs);
        this.overloadPeakThresholdMs = Math.max(this.overloadAverageThresholdMs, overloadPeakThresholdMs);
        this.heavyUsageThresholdPercent = Math.max(1, Math.min(100, heavyUsageThresholdPercent));
        this.heavyConsecutiveWindowsThreshold = Math.max(1, heavyConsecutiveWindowsThreshold);
        this.overloadConsecutiveWindowsThreshold = Math.max(1, overloadConsecutiveWindowsThreshold);
        this.heavyDelayedThresholdPercent = Math.max(1, Math.min(100, heavyDelayedThresholdPercent));
        this.maxHistoryEntries = Math.max(10, maxHistoryEntries);
        this.logs = new EnumMap<>(Type.class);
        this.history = new ArrayDeque<>(this.maxHistoryEntries);

        for (Type type : Type.values()) {
            this.logs.put(type, new LogEntry(type, defaultSeverity(type)));
        }
    }

    public boolean tryConsumeExecutionBudget(int estimatedCost, long now, String sourceLabel, int sourceId, String reason) {
        rollWindowIfNeeded(now);

        int normalizedCost = Math.max(0, estimatedCost);
        int currentUsage = this.usageCurrentWindow.addAndGet(normalizedCost);
        if (currentUsage > this.usageLimitPerWindow) {
            record(Type.EXECUTION_CAP, now,
                    buildExecutionCapReason(normalizedCost, reason, currentUsage),
                    sourceLabel,
                    sourceId);
            return false;
        }

        return true;
    }

    public boolean tryScheduleDelayedEvent(long now, String sourceLabel, int sourceId, String reason) {
        rollWindowIfNeeded(now);

        int currentPending = this.delayedEventsPending.incrementAndGet();
        if (currentPending > this.delayedEventsLimit) {
            record(Type.DELAYED_EVENTS_CAP, now,
                    buildDelayedCapReason(reason, currentPending),
                    sourceLabel,
                    sourceId);
            return false;
        }

        return true;
    }

    public void completeDelayedEvent() {
        this.delayedEventsPending.updateAndGet(v -> v > 0 ? v - 1 : 0);
    }

    public void recordExecution(long elapsedMs, long now, String sourceLabel, int sourceId, String reason) {
        rollWindowIfNeeded(now);

        int normalizedElapsed = (int) Math.max(0L, elapsedMs);

        long total = this.totalExecutionMsCurrentWindow.addAndGet(normalizedElapsed);
        int samples = this.executionSamplesCurrentWindow.incrementAndGet();
        this.averageExecutionMs = (int) Math.round(total / (double) samples);

        if (normalizedElapsed >= this.peakExecutionMs) {
            synchronized (this) {
                if (normalizedElapsed >= this.peakExecutionMs) {
                    this.peakExecutionMs = normalizedElapsed;
                    this.peakExecutionSourceLabel = sanitizeSourceLabel(sourceLabel);
                    this.peakExecutionSourceId = Math.max(0, sourceId);
                    this.peakExecutionReason = sanitizeReason(reason);
                }
            }
        }
    }

    public void recordKilled(long now, String reason, String sourceLabel, int sourceId) {
        rollWindowIfNeeded(now);
        record(Type.KILLED, now, reason, sourceLabel, sourceId);
    }

    public void recordRecursionTimeout(long now, String reason, String sourceLabel, int sourceId) {
        rollWindowIfNeeded(now);
        record(Type.RECURSION_TIMEOUT, now, reason, sourceLabel, sourceId);
    }

    public synchronized void clearLogs() {
        for (Type type : Type.values()) {
            LogEntry entry = this.logs.get(type);

            if (entry == null) {
                continue;
            }

            entry.count = 0;
            entry.firstOccurredAtMs = 0L;
            entry.lastOccurredAtMs = 0L;
            entry.latestReason = "";
            entry.latestSourceLabel = "";
            entry.latestSourceId = 0;
        }

        this.history.clear();
    }

    public synchronized Snapshot snapshot(int recursionDepthCurrent, int recursionDepthLimit, long killedUntilMs, long now) {
        rollWindowIfNeeded(now);

        List<LogEntry> logEntries = new ArrayList<>(Type.values().length);
        List<HistoryEntry> historyEntries = new ArrayList<>(this.history.size());

        for (Type type : Type.values()) {
            LogEntry source = this.logs.get(type);
            LogEntry copy = new LogEntry(source.getType(), source.getSeverity());

            copy.count = source.getCount();
            copy.firstOccurredAtMs = source.getFirstOccurredAtMs();
            copy.lastOccurredAtMs = source.getLastOccurredAtMs();
            copy.latestReason = source.getLatestReason();
            copy.latestSourceLabel = source.getLatestSourceLabel();
            copy.latestSourceId = source.getLatestSourceId();

            logEntries.add(copy);
        }

        historyEntries.addAll(this.history);

        int killedRemainingSeconds = 0;

        if (killedUntilMs > now) {
            killedRemainingSeconds = (int) Math.max(0L, Math.ceil((killedUntilMs - now) / 1000D));
        }

        return new Snapshot(
                this.usageCurrentWindow.get(),
                this.usageLimitPerWindow,
                this.heavy,
                this.delayedEventsPending.get(),
                this.delayedEventsLimit,
                this.averageExecutionMs,
                this.peakExecutionMs,
                recursionDepthCurrent,
                recursionDepthLimit,
                killedRemainingSeconds,
                this.usageWindowMs,
                this.overloadAverageThresholdMs,
                this.overloadPeakThresholdMs,
                this.heavyUsageThresholdPercent,
                this.heavyConsecutiveWindowsThreshold,
                this.overloadConsecutiveWindowsThreshold,
                this.heavyDelayedThresholdPercent,
                logEntries,
                historyEntries
        );
    }

    private void rollWindowIfNeeded(long now) {
        long startedAt = this.windowStartedAt.get();
        if (startedAt <= 0L) {
            this.windowStartedAt.compareAndSet(startedAt, now);
            return;
        }

        if ((now - startedAt) >= this.usageWindowMs) {
            synchronized (this) {
                startedAt = this.windowStartedAt.get();
                if ((now - startedAt) >= this.usageWindowMs) {
                    while ((now - startedAt) >= this.usageWindowMs) {
                        evaluateWindow(startedAt + this.usageWindowMs);
                        startedAt += this.usageWindowMs;
                        
                        this.usageCurrentWindow.set(0);
                        this.totalExecutionMsCurrentWindow.set(0L);
                        this.executionSamplesCurrentWindow.set(0);
                        this.averageExecutionMs = 0;
                        this.peakExecutionMs = 0;
                        this.peakExecutionSourceLabel = null;
                        this.peakExecutionSourceId = 0;
                        this.peakExecutionReason = null;
                    }
                    this.windowStartedAt.set(startedAt);
                }
            }
        }
    }

    private void evaluateWindow(long now) {
        int usagePercent = (int) Math.round((this.usageCurrentWindow.get() * 100D) / this.usageLimitPerWindow);
        int delayedPercent = (int) Math.round((this.delayedEventsPending.get() * 100D) / this.delayedEventsLimit);
        boolean overloadWindow = (this.executionSamplesCurrentWindow.get() > 0)
                && ((this.averageExecutionMs >= this.overloadAverageThresholdMs) || (this.peakExecutionMs >= this.overloadPeakThresholdMs));
        boolean heavyWindow = (usagePercent >= this.heavyUsageThresholdPercent)
                || (delayedPercent >= this.heavyDelayedThresholdPercent)
                || overloadWindow;

        if (overloadWindow) {
            this.consecutiveOverloadWindows++;

            if (this.consecutiveOverloadWindows >= this.overloadConsecutiveWindowsThreshold) {
                record(Type.EXECUTOR_OVERLOAD, now,
                        buildExecutorOverloadReason(),
                        this.peakExecutionSourceLabel,
                        this.peakExecutionSourceId);
            }
        } else {
            this.consecutiveOverloadWindows = 0;
        }

        if (heavyWindow) {
            this.consecutiveHeavyWindows++;

            if (!this.heavy && (this.consecutiveHeavyWindows >= this.heavyConsecutiveWindowsThreshold)) {
                this.heavy = true;
                record(Type.MARKED_AS_HEAVY, now,
                        buildHeavyReason(usagePercent, delayedPercent, overloadWindow),
                        overloadWindow ? this.peakExecutionSourceLabel : null,
                        overloadWindow ? this.peakExecutionSourceId : 0);
            }

            return;
        }

        this.consecutiveHeavyWindows = 0;
        this.heavy = false;
    }

    private void record(Type type, long now, String reason, String sourceLabel, int sourceId) {
        LogEntry entry = this.logs.get(type);
        if (entry != null) {
            entry.record(now, reason, sourceLabel, sourceId);
            this.history.addFirst(new HistoryEntry(type, entry.getSeverity(), now, reason, sourceLabel, sourceId));

            while (this.history.size() > this.maxHistoryEntries) {
                this.history.removeLast();
            }
        }
    }

    private String buildExecutionCapReason(int normalizedCost, String reason, int currentUsage) {
        return joinReason(
                reason,
                String.format("Estimated stack cost %d would exceed usage budget %d/%d in %dms window",
                        normalizedCost,
                        currentUsage,
                        this.usageLimitPerWindow,
                        this.usageWindowMs)
        );
    }

    private String buildDelayedCapReason(String reason, int currentPending) {
        return joinReason(
                reason,
                String.format("Pending delayed events would exceed queue %d/%d",
                        currentPending,
                        this.delayedEventsLimit)
        );
    }

    private String buildExecutorOverloadReason() {
        return joinReason(
                this.peakExecutionReason,
                String.format("Average execution %dms (limit %dms), peak %dms (limit %dms) across %d execution(s) in %dms window",
                        this.averageExecutionMs,
                        this.overloadAverageThresholdMs,
                        this.peakExecutionMs,
                        this.overloadPeakThresholdMs,
                        this.executionSamplesCurrentWindow.get(),
                        this.usageWindowMs)
        );
    }

    private String buildHeavyReason(int usagePercent, int delayedPercent, boolean overloadWindow) {
        return String.format(
                "Room stayed above heavy thresholds for %d consecutive window(s): usage %d%%/%d%%, delayed %d%%/%d%%, overload %s",
                this.consecutiveHeavyWindows,
                usagePercent,
                this.heavyUsageThresholdPercent,
                delayedPercent,
                this.heavyDelayedThresholdPercent,
                overloadWindow ? "yes" : "no"
        );
    }

    private static String joinReason(String primary, String fallback) {
        String cleanPrimary = sanitizeReason(primary);
        String cleanFallback = sanitizeReason(fallback);

        if (cleanPrimary.isEmpty()) return cleanFallback;
        if (cleanFallback.isEmpty()) return cleanPrimary;
        if (cleanPrimary.equals(cleanFallback)) return cleanPrimary;

        return cleanPrimary + ". " + cleanFallback;
    }

    private static String sanitizeReason(String reason) {
        return (reason == null) ? "" : reason.trim();
    }

    private static String sanitizeSourceLabel(String sourceLabel) {
        return (sourceLabel == null) ? "" : sourceLabel.trim();
    }

    private Severity defaultSeverity(Type type) {
        return (type == Type.MARKED_AS_HEAVY) ? Severity.WARNING : Severity.ERROR;
    }
}
