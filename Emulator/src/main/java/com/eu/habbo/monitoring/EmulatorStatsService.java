package com.eu.habbo.monitoring;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredRoomDiagnostics;
import com.eu.habbo.habbohotel.wired.tick.WiredTickService;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public final class EmulatorStatsService {
    private static final long CACHE_TTL_MS = 1_000L;
    private static final int MAX_HISTORY_POINTS = 90;

    private static final ArrayDeque<MemoryPoint> MEMORY_HISTORY = new ArrayDeque<>();

    private static volatile Snapshot cachedSnapshot = null;
    private static volatile long cachedAt = 0L;
    private static volatile int peakPlayers = 0;
    private static volatile int peakWebSocketSessions = 0;
    private static volatile long previousIncomingPackets = 0L;
    private static volatile long previousOutgoingPackets = 0L;
    private static volatile long previousIncomingBytes = 0L;
    private static volatile long previousOutgoingBytes = 0L;
    private static volatile long previousGcCount = 0L;
    private static volatile long previousGcTimeMs = 0L;
    private static volatile long previousTelemetryAt = 0L;

    private EmulatorStatsService() {
    }

    public static Snapshot collectSnapshot() {
        long now = System.currentTimeMillis();
        Snapshot current = cachedSnapshot;

        if (current != null && (now - cachedAt) < CACHE_TTL_MS) {
            return current;
        }

        synchronized (EmulatorStatsService.class) {
            current = cachedSnapshot;

            if (current != null && (now - cachedAt) < CACHE_TTL_MS) {
                return current;
            }

            Snapshot built = buildSnapshot(now);
            cachedSnapshot = built;
            cachedAt = now;
            return built;
        }
    }

    public static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
    }

    private static Snapshot buildSnapshot(long now) {
        Runtime runtime = Runtime.getRuntime();

        long totalMemBytes = runtime.totalMemory();
        long freeMemBytes = runtime.freeMemory();
        long usedMemBytes = totalMemBytes - freeMemBytes;
        long maxMemBytes = runtime.maxMemory();

        int usedMemMb = (int) (usedMemBytes / 1024L / 1024L);
        int maxMemMb = (int) (maxMemBytes / 1024L / 1024L);
        int estimatedAllocMb = (int) (totalMemBytes / 1024L / 1024L);
        double memoryUsagePercent = maxMemBytes > 0
                ? (usedMemBytes * 100D) / maxMemBytes
                : 0D;

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double cpuLoadPercent = 0D;

        if (osBean instanceof com.sun.management.OperatingSystemMXBean managedOsBean) {
            cpuLoadPercent = Math.max(0D, managedOsBean.getProcessCpuLoad() * 100D);
        }

        int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();

        List<Habbo> habbos = List.of();
        List<Room> rooms = List.of();
        int webSocketSessions = 0;

        if (Emulator.getGameEnvironment() != null) {
            if (Emulator.getGameEnvironment().getHabboManager() != null) {
                habbos = Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values().stream().toList();
            }

            if (Emulator.getGameEnvironment().getRoomManager() != null) {
                rooms = Emulator.getGameEnvironment().getRoomManager().getActiveRooms();
            }
        }

        if (Emulator.getGameServer() != null && Emulator.getGameServer().getGameClientManager() != null) {
            webSocketSessions = Emulator.getGameServer().getGameClientManager().getSessions().size();
        }

        peakPlayers = Math.max(peakPlayers, habbos.size());
        peakWebSocketSessions = Math.max(peakWebSocketSessions, webSocketSessions);

        WiredTickService wiredTickService = WiredTickService.getInstance();
        int totalTickables = (wiredTickService != null) ? wiredTickService.getTotalTickableCount() : 0;

        appendMemoryHistory(now, usedMemMb, maxMemMb, memoryUsagePercent);

        double averageRoomCycleMs = 0D;
        double worstRoomCycleMs = 0D;
        int worstRoomCycleRoomId = 0;
        String worstRoomCycleRoomName = "-";

        long totalDelayedEventsPending = 0L;
        int overloadedWiredRooms = 0;
        int heavyWiredRooms = 0;
        double wiredActivityPerSecond = 0D;

        List<OnlineUserRow> users = new ArrayList<>(habbos.size());
        for (Habbo habbo : habbos) {
            int roomId = (habbo.getHabboInfo().getCurrentRoom() != null) ? habbo.getHabboInfo().getCurrentRoom().getId() : 0;

            users.add(new OnlineUserRow(
                    habbo.getHabboInfo().getId(),
                    habbo.getHabboInfo().getUsername(),
                    habbo.getHabboInfo().getRank().getName(),
                    habbo.getHabboInfo().getCurrencyAmount(0),
                    roomId
            ));
        }

        List<ActiveRoomRow> activeRooms = new ArrayList<>(rooms.size());
        List<WiredRoomRow> wiredRooms = new ArrayList<>();
        List<WiredTopRoomRow> wiredTopRooms = new ArrayList<>();

        double roomCycleAccumulator = 0D;
        int roomCycleSamples = 0;

        for (Room room : rooms) {
            int tickables = (wiredTickService != null) ? wiredTickService.getTickableCount(room.getId()) : 0;
            double roomCycleMs = Math.max(0D, room.lastCycleCpuMs);

            roomCycleAccumulator += roomCycleMs;
            roomCycleSamples++;

            if (roomCycleMs >= worstRoomCycleMs) {
                worstRoomCycleMs = roomCycleMs;
                worstRoomCycleRoomId = room.getId();
                worstRoomCycleRoomName = room.getName();
            }

            activeRooms.add(new ActiveRoomRow(
                    room.getId(),
                    room.getName(),
                    room.getUserCount(),
                    room.itemCount(),
                    tickables,
                    room.lastCycleCpuMs,
                    room.getEstimatedMemoryUsage() / 1024L,
                    room.lastCycleThread
            ));

            WiredRoomDiagnostics.Snapshot diagnostics = WiredManager.getDiagnosticsSnapshot(room.getId());

            if (diagnostics == null) {
                continue;
            }

            boolean shouldShow = diagnostics.getAverageExecutionMs() > 0
                    || diagnostics.getPeakExecutionMs() > 0
                    || tickables > 0
                    || diagnostics.getDelayedEventsPending() > 0;

            if (!shouldShow) {
                continue;
            }

            int usagePercent = (int) Math.round((diagnostics.getUsageCurrentWindow() * 100D) / Math.max(1, diagnostics.getUsageLimitPerWindow()));
            double roomActivityPerSecond = (diagnostics.getUsageCurrentWindow() * 1000D) / Math.max(1, diagnostics.getUsageWindowMs());

            totalDelayedEventsPending += diagnostics.getDelayedEventsPending();
            wiredActivityPerSecond += roomActivityPerSecond;

            if (diagnostics.getAverageExecutionMs() >= diagnostics.getOverloadAverageThresholdMs()) {
                overloadedWiredRooms++;
            }

            if (diagnostics.isHeavy()) {
                heavyWiredRooms++;
            }

            wiredRooms.add(new WiredRoomRow(
                    room.getId(),
                    diagnostics.getAverageExecutionMs(),
                    diagnostics.getPeakExecutionMs(),
                    usagePercent,
                    diagnostics.getDelayedEventsPending(),
                    diagnostics.getAverageExecutionMs() >= diagnostics.getOverloadAverageThresholdMs(),
                    diagnostics.isHeavy()
            ));

            wiredTopRooms.add(new WiredTopRoomRow(
                    room.getId(),
                    room.getName(),
                    usagePercent,
                    diagnostics.getAverageExecutionMs(),
                    diagnostics.getPeakExecutionMs(),
                    diagnostics.getDelayedEventsPending(),
                    roomActivityPerSecond,
                    diagnostics.isHeavy()
            ));
        }

        if (roomCycleSamples > 0) {
            averageRoomCycleMs = roomCycleAccumulator / roomCycleSamples;
        }

        wiredTopRooms.sort(Comparator
                .comparingInt((WiredTopRoomRow row) -> row.usagePercent).reversed()
                .thenComparingInt(row -> row.averageTickMs).reversed()
                .thenComparingInt(row -> row.peakTickMs).reversed());

        if (wiredTopRooms.size() > 5) {
            wiredTopRooms = new ArrayList<>(wiredTopRooms.subList(0, 5));
        }

        HikariPoolMetrics hikariPoolMetrics = collectHikariPoolMetrics();
        SchedulerMetrics schedulerMetrics = collectSchedulerMetrics();
        NetworkMetrics networkMetrics = collectNetworkMetrics(now);
        GarbageCollectorMetrics garbageCollectorMetrics = collectGarbageCollectorMetrics(now);

        Overview overview = new Overview(
                Emulator.getOnlineTime(),
                now,
                cpuLoadPercent >= 80D ? "Attention needed" : "Healthy",
                usedMemMb,
                maxMemMb,
                estimatedAllocMb,
                memoryUsagePercent,
                cpuLoadPercent,
                threadCount,
                habbos.size(),
                rooms.size(),
                totalTickables,
                peakPlayers,
                webSocketSessions,
                peakWebSocketSessions,
                averageRoomCycleMs,
                worstRoomCycleMs,
                worstRoomCycleRoomId,
                worstRoomCycleRoomName,
                totalDelayedEventsPending,
                overloadedWiredRooms,
                heavyWiredRooms,
                wiredActivityPerSecond
        );

        return new Snapshot(
                overview,
                new ArrayList<>(MEMORY_HISTORY),
                users,
                activeRooms,
                wiredRooms,
                wiredTopRooms,
                hikariPoolMetrics,
                schedulerMetrics,
                networkMetrics,
                garbageCollectorMetrics
        );
    }

    private static HikariPoolMetrics collectHikariPoolMetrics() {
        HikariDataSource dataSource = (Emulator.getDatabase() != null) ? Emulator.getDatabase().getDataSource() : null;
        HikariPoolMXBean poolMxBean = (dataSource != null) ? dataSource.getHikariPoolMXBean() : null;

        if (poolMxBean == null) {
            return new HikariPoolMetrics(0, 0, 0, 0, 0);
        }

        return new HikariPoolMetrics(
                poolMxBean.getActiveConnections(),
                poolMxBean.getIdleConnections(),
                poolMxBean.getTotalConnections(),
                poolMxBean.getThreadsAwaitingConnection(),
                dataSource.getMaximumPoolSize()
        );
    }

    private static SchedulerMetrics collectSchedulerMetrics() {
        if (Emulator.getThreading() == null) {
            return new SchedulerMetrics(0, 0, 0, 0, false);
        }

        if (!(Emulator.getThreading().getService() instanceof ScheduledThreadPoolExecutor executor)) {
            return new SchedulerMetrics(0, 0, 0, 0, false);
        }

        return new SchedulerMetrics(
                executor.getQueue().size(),
                executor.getActiveCount(),
                executor.getPoolSize(),
                executor.getCompletedTaskCount(),
                !executor.isShutdown()
        );
    }

    private static NetworkMetrics collectNetworkMetrics(long now) {
        long incomingPackets = EmulatorNetworkStats.getIncomingPackets();
        long outgoingPackets = EmulatorNetworkStats.getOutgoingPackets();
        long incomingBytes = EmulatorNetworkStats.getIncomingBytes();
        long outgoingBytes = EmulatorNetworkStats.getOutgoingBytes();

        long previousAt = previousTelemetryAt;
        long elapsedMs = (previousAt > 0L) ? Math.max(1L, now - previousAt) : CACHE_TTL_MS;

        double incomingPacketsPerSecond = ((incomingPackets - previousIncomingPackets) * 1000D) / elapsedMs;
        double outgoingPacketsPerSecond = ((outgoingPackets - previousOutgoingPackets) * 1000D) / elapsedMs;
        double incomingKilobytesPerSecond = ((incomingBytes - previousIncomingBytes) / 1024D) * 1000D / elapsedMs;
        double outgoingKilobytesPerSecond = ((outgoingBytes - previousOutgoingBytes) / 1024D) * 1000D / elapsedMs;

        previousIncomingPackets = incomingPackets;
        previousOutgoingPackets = outgoingPackets;
        previousIncomingBytes = incomingBytes;
        previousOutgoingBytes = outgoingBytes;
        previousTelemetryAt = now;

        return new NetworkMetrics(
                Math.max(0D, incomingPacketsPerSecond),
                Math.max(0D, outgoingPacketsPerSecond),
                Math.max(0D, incomingKilobytesPerSecond),
                Math.max(0D, outgoingKilobytesPerSecond),
                incomingPackets,
                outgoingPackets
        );
    }

    private static GarbageCollectorMetrics collectGarbageCollectorMetrics(long now) {
        long totalCollections = 0L;
        long totalCollectionTimeMs = 0L;

        for (GarbageCollectorMXBean garbageCollectorMXBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long collectionCount = garbageCollectorMXBean.getCollectionCount();
            long collectionTime = garbageCollectorMXBean.getCollectionTime();

            if (collectionCount > 0) {
                totalCollections += collectionCount;
            }

            if (collectionTime > 0) {
                totalCollectionTimeMs += collectionTime;
            }
        }

        long lastObservedPauseMs = Math.max(0L, totalCollectionTimeMs - previousGcTimeMs);
        long collectionsSinceLastSample = Math.max(0L, totalCollections - previousGcCount);

        previousGcCount = totalCollections;
        previousGcTimeMs = totalCollectionTimeMs;

        return new GarbageCollectorMetrics(
                totalCollections,
                totalCollectionTimeMs,
                collectionsSinceLastSample,
                lastObservedPauseMs,
                now
        );
    }

    private static void appendMemoryHistory(long timestamp, int usedMemMb, int maxMemMb, double usagePercent) {
        MEMORY_HISTORY.addLast(new MemoryPoint(timestamp, usedMemMb, maxMemMb, usagePercent));

        while (MEMORY_HISTORY.size() > MAX_HISTORY_POINTS) {
            MEMORY_HISTORY.removeFirst();
        }
    }

    public static final class Snapshot {
        public final Overview overview;
        public final List<MemoryPoint> memoryHistory;
        public final List<OnlineUserRow> users;
        public final List<ActiveRoomRow> rooms;
        public final List<WiredRoomRow> wired;
        public final List<WiredTopRoomRow> wiredTopRooms;
        public final HikariPoolMetrics databasePool;
        public final SchedulerMetrics scheduler;
        public final NetworkMetrics network;
        public final GarbageCollectorMetrics garbageCollector;

        public Snapshot(Overview overview, List<MemoryPoint> memoryHistory, List<OnlineUserRow> users, List<ActiveRoomRow> rooms, List<WiredRoomRow> wired, List<WiredTopRoomRow> wiredTopRooms, HikariPoolMetrics databasePool, SchedulerMetrics scheduler, NetworkMetrics network, GarbageCollectorMetrics garbageCollector) {
            this.overview = overview;
            this.memoryHistory = memoryHistory;
            this.users = users;
            this.rooms = rooms;
            this.wired = wired;
            this.wiredTopRooms = wiredTopRooms;
            this.databasePool = databasePool;
            this.scheduler = scheduler;
            this.network = network;
            this.garbageCollector = garbageCollector;
        }
    }

    public static final class Overview {
        public final long uptimeSeconds;
        public final long lastRefreshEpochMs;
        public final String guiStatus;
        public final int memoryUsedMb;
        public final int memoryMaxMb;
        public final int memoryAllocatedMb;
        public final double memoryUsagePercent;
        public final double cpuLoadPercent;
        public final int activeOsThreads;
        public final int connectedPlayers;
        public final int loadedRooms;
        public final int wiredTickables;
        public final int peakPlayers;
        public final int activeWebSocketSessions;
        public final int peakWebSocketSessions;
        public final double averageRoomCycleMs;
        public final double worstRoomCycleMs;
        public final int worstRoomCycleRoomId;
        public final String worstRoomCycleRoomName;
        public final long delayedEventsPending;
        public final int overloadedWiredRooms;
        public final int heavyWiredRooms;
        public final double wiredActivityPerSecond;

        public Overview(long uptimeSeconds, long lastRefreshEpochMs, String guiStatus, int memoryUsedMb, int memoryMaxMb, int memoryAllocatedMb, double memoryUsagePercent, double cpuLoadPercent, int activeOsThreads, int connectedPlayers, int loadedRooms, int wiredTickables, int peakPlayers, int activeWebSocketSessions, int peakWebSocketSessions, double averageRoomCycleMs, double worstRoomCycleMs, int worstRoomCycleRoomId, String worstRoomCycleRoomName, long delayedEventsPending, int overloadedWiredRooms, int heavyWiredRooms, double wiredActivityPerSecond) {
            this.uptimeSeconds = uptimeSeconds;
            this.lastRefreshEpochMs = lastRefreshEpochMs;
            this.guiStatus = guiStatus;
            this.memoryUsedMb = memoryUsedMb;
            this.memoryMaxMb = memoryMaxMb;
            this.memoryAllocatedMb = memoryAllocatedMb;
            this.memoryUsagePercent = memoryUsagePercent;
            this.cpuLoadPercent = cpuLoadPercent;
            this.activeOsThreads = activeOsThreads;
            this.connectedPlayers = connectedPlayers;
            this.loadedRooms = loadedRooms;
            this.wiredTickables = wiredTickables;
            this.peakPlayers = peakPlayers;
            this.activeWebSocketSessions = activeWebSocketSessions;
            this.peakWebSocketSessions = peakWebSocketSessions;
            this.averageRoomCycleMs = averageRoomCycleMs;
            this.worstRoomCycleMs = worstRoomCycleMs;
            this.worstRoomCycleRoomId = worstRoomCycleRoomId;
            this.worstRoomCycleRoomName = worstRoomCycleRoomName;
            this.delayedEventsPending = delayedEventsPending;
            this.overloadedWiredRooms = overloadedWiredRooms;
            this.heavyWiredRooms = heavyWiredRooms;
            this.wiredActivityPerSecond = wiredActivityPerSecond;
        }
    }

    public static final class MemoryPoint {
        public final long timestamp;
        public final int usedMb;
        public final int maxMb;
        public final double usagePercent;

        public MemoryPoint(long timestamp, int usedMb, int maxMb, double usagePercent) {
            this.timestamp = timestamp;
            this.usedMb = usedMb;
            this.maxMb = maxMb;
            this.usagePercent = usagePercent;
        }
    }

    public static final class OnlineUserRow {
        public final int id;
        public final String username;
        public final String rank;
        public final int credits;
        public final int roomId;

        public OnlineUserRow(int id, String username, String rank, int credits, int roomId) {
            this.id = id;
            this.username = username;
            this.rank = rank;
            this.credits = credits;
            this.roomId = roomId;
        }
    }

    public static final class ActiveRoomRow {
        public final int roomId;
        public final String name;
        public final int players;
        public final int items;
        public final int tickables;
        public final double cpuMs;
        public final long estimatedRamKb;
        public final String thread;

        public ActiveRoomRow(int roomId, String name, int players, int items, int tickables, double cpuMs, long estimatedRamKb, String thread) {
            this.roomId = roomId;
            this.name = name;
            this.players = players;
            this.items = items;
            this.tickables = tickables;
            this.cpuMs = cpuMs;
            this.estimatedRamKb = estimatedRamKb;
            this.thread = thread;
        }
    }

    public static final class WiredRoomRow {
        public final int roomId;
        public final long averageTickMs;
        public final long peakTickMs;
        public final int usagePercent;
        public final int delayedEventsPending;
        public final boolean overloaded;
        public final boolean heavy;

        public WiredRoomRow(int roomId, long averageTickMs, long peakTickMs, int usagePercent, int delayedEventsPending, boolean overloaded, boolean heavy) {
            this.roomId = roomId;
            this.averageTickMs = averageTickMs;
            this.peakTickMs = peakTickMs;
            this.usagePercent = usagePercent;
            this.delayedEventsPending = delayedEventsPending;
            this.overloaded = overloaded;
            this.heavy = heavy;
        }
    }

    public static final class WiredTopRoomRow {
        public final int roomId;
        public final String name;
        public final int usagePercent;
        public final int averageTickMs;
        public final int peakTickMs;
        public final int delayedEventsPending;
        public final double activityPerSecond;
        public final boolean heavy;

        public WiredTopRoomRow(int roomId, String name, int usagePercent, int averageTickMs, int peakTickMs, int delayedEventsPending, double activityPerSecond, boolean heavy) {
            this.roomId = roomId;
            this.name = name;
            this.usagePercent = usagePercent;
            this.averageTickMs = averageTickMs;
            this.peakTickMs = peakTickMs;
            this.delayedEventsPending = delayedEventsPending;
            this.activityPerSecond = activityPerSecond;
            this.heavy = heavy;
        }
    }

    public static final class HikariPoolMetrics {
        public final int activeConnections;
        public final int idleConnections;
        public final int totalConnections;
        public final int waitingThreads;
        public final int maxConnections;

        public HikariPoolMetrics(int activeConnections, int idleConnections, int totalConnections, int waitingThreads, int maxConnections) {
            this.activeConnections = activeConnections;
            this.idleConnections = idleConnections;
            this.totalConnections = totalConnections;
            this.waitingThreads = waitingThreads;
            this.maxConnections = maxConnections;
        }
    }

    public static final class SchedulerMetrics {
        public final int queuedTasks;
        public final int activeThreads;
        public final int poolSize;
        public final long completedTasks;
        public final boolean running;

        public SchedulerMetrics(int queuedTasks, int activeThreads, int poolSize, long completedTasks, boolean running) {
            this.queuedTasks = queuedTasks;
            this.activeThreads = activeThreads;
            this.poolSize = poolSize;
            this.completedTasks = completedTasks;
            this.running = running;
        }
    }

    public static final class NetworkMetrics {
        public final double incomingPacketsPerSecond;
        public final double outgoingPacketsPerSecond;
        public final double incomingKilobytesPerSecond;
        public final double outgoingKilobytesPerSecond;
        public final long totalIncomingPackets;
        public final long totalOutgoingPackets;

        public NetworkMetrics(double incomingPacketsPerSecond, double outgoingPacketsPerSecond, double incomingKilobytesPerSecond, double outgoingKilobytesPerSecond, long totalIncomingPackets, long totalOutgoingPackets) {
            this.incomingPacketsPerSecond = incomingPacketsPerSecond;
            this.outgoingPacketsPerSecond = outgoingPacketsPerSecond;
            this.incomingKilobytesPerSecond = incomingKilobytesPerSecond;
            this.outgoingKilobytesPerSecond = outgoingKilobytesPerSecond;
            this.totalIncomingPackets = totalIncomingPackets;
            this.totalOutgoingPackets = totalOutgoingPackets;
        }
    }

    public static final class GarbageCollectorMetrics {
        public final long totalCollections;
        public final long totalCollectionTimeMs;
        public final long collectionsSinceLastSample;
        public final long lastObservedPauseMs;
        public final long sampledAtEpochMs;

        public GarbageCollectorMetrics(long totalCollections, long totalCollectionTimeMs, long collectionsSinceLastSample, long lastObservedPauseMs, long sampledAtEpochMs) {
            this.totalCollections = totalCollections;
            this.totalCollectionTimeMs = totalCollectionTimeMs;
            this.collectionsSinceLastSample = collectionsSinceLastSample;
            this.lastObservedPauseMs = lastObservedPauseMs;
            this.sampledAtEpochMs = sampledAtEpochMs;
        }
    }
}
