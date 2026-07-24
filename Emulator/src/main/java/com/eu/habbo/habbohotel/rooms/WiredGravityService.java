package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Room-owned, session-only runtime for the opt-in {@code @gravity} furniture variable. */
final class WiredGravityService {
    static final int DEFAULT_MAXIMUM_ITEMS = 1_000;
    static final int ABSOLUTE_MAXIMUM_ITEMS = 10_000;
    static final int DEFAULT_SETTLE_DELAY_MS = 75;
    static final int DEFAULT_RETRY_DELAY_MS = 50;

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredGravityService.class);

    private final Room room;
    private final Scheduler scheduler;
    private final LongSupplier clock;
    private final int maximumItems;
    private final int settleDelayMs;
    private final int retryDelayMs;
    private final Set<Integer> enabledItemIds = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Long> movingUntil = new ConcurrentHashMap<>();
    private final Object taskLock = new Object();

    private long generation = 1L;
    private Cancellable pendingTask;
    private boolean settling;
    private boolean topologyChangedDuringSettle;

    WiredGravityService(Room room) {
        this(
                room,
                (task, delayMs) -> {
                    ScheduledFuture<?> future = WiredPlatform.threading().run(task, delayMs);
                    return future == null ? () -> false : () -> future.cancel(false);
                },
                System::currentTimeMillis,
                configuredInt("wired.gravity.max_items_per_room", DEFAULT_MAXIMUM_ITEMS, 1, ABSOLUTE_MAXIMUM_ITEMS),
                configuredInt("wired.gravity.settle_delay_ms", DEFAULT_SETTLE_DELAY_MS, 1, 5_000),
                configuredInt("wired.gravity.retry_delay_ms", DEFAULT_RETRY_DELAY_MS, 1, 5_000));
    }

    WiredGravityService(
            Room room, Scheduler scheduler, LongSupplier clock, int maximumItems, int settleDelayMs, int retryDelayMs) {
        this.room = room;
        this.scheduler = scheduler;
        this.clock = clock;
        this.maximumItems = Math.max(1, Math.min(ABSOLUTE_MAXIMUM_ITEMS, maximumItems));
        this.settleDelayMs = Math.max(1, settleDelayMs);
        this.retryDelayMs = Math.max(1, retryDelayMs);
    }

    boolean setEnabled(HabboItem item, boolean enabled) {
        if (!isEligibleFloorItem(item) || item.getRoomId() != room.getId()) {
            return false;
        }
        if (!enabled) {
            enabledItemIds.remove(item.getId());
            movingUntil.remove(item.getId());
            return true;
        }
        if (!enabledItemIds.contains(item.getId()) && enabledItemIds.size() >= maximumItems) {
            LOGGER.warn(
                    "Gravity item limit reached: room={}, configuredLimit={}, rejectedItem={}",
                    room.getId(),
                    maximumItems,
                    item.getId());
            return false;
        }
        enabledItemIds.add(item.getId());
        schedule(settleDelayMs);
        return true;
    }

    boolean isEnabled(HabboItem item) {
        return isEligibleFloorItem(item) && item.getRoomId() == room.getId() && enabledItemIds.contains(item.getId());
    }

    void forget(HabboItem item) {
        if (item == null) {
            return;
        }
        enabledItemIds.remove(item.getId());
        movingUntil.remove(item.getId());
    }

    void onTopologyChanged() {
        if (enabledItemIds.isEmpty()) {
            return;
        }
        synchronized (taskLock) {
            if (settling) {
                topologyChangedDuringSettle = true;
                return;
            }
        }
        schedule(settleDelayMs);
    }

    void markMoving(HabboItem item, int durationMs) {
        if (item == null || !enabledItemIds.contains(item.getId())) {
            return;
        }
        movingUntil.merge(item.getId(), clock.getAsLong() + Math.max(1, durationMs), Math::max);
        onTopologyChanged();
    }

    void dispose() {
        synchronized (taskLock) {
            generation++;
            if (pendingTask != null) {
                pendingTask.cancel();
                pendingTask = null;
            }
            settling = false;
            topologyChangedDuringSettle = false;
        }
        enabledItemIds.clear();
        movingUntil.clear();
    }

    int enabledCount() {
        return enabledItemIds.size();
    }

    long generation() {
        synchronized (taskLock) {
            return generation;
        }
    }

    boolean hasPendingTask() {
        synchronized (taskLock) {
            return pendingTask != null;
        }
    }

    private void schedule(long delayMs) {
        if (enabledItemIds.isEmpty() || room == null || !room.isLoaded()) {
            return;
        }
        synchronized (taskLock) {
            if (pendingTask != null) {
                return;
            }
            long expectedGeneration = generation;
            pendingTask = scheduler.schedule(() -> runScheduled(expectedGeneration), Math.max(1L, delayMs));
        }
    }

    private void runScheduled(long expectedGeneration) {
        synchronized (taskLock) {
            pendingTask = null;
            if (generation != expectedGeneration || settling) {
                return;
            }
            settling = true;
            topologyChangedDuringSettle = false;
        }

        long retryAfterMs = 0L;
        try {
            if (!room.isLoaded()) {
                return;
            }
            retryAfterMs = settle(expectedGeneration);
        } finally {
            boolean topologyChanged;
            synchronized (taskLock) {
                settling = false;
                topologyChanged = topologyChangedDuringSettle;
                topologyChangedDuringSettle = false;
            }
            if (retryAfterMs > 0L || topologyChanged) {
                schedule(Math.max(retryDelayMs, retryAfterMs));
            }
        }
    }

    private long settle(long expectedGeneration) {
        WiredGravityPlanner.Snapshot snapshot = snapshotRoom();
        WiredGravityPlanner.Plan plan = WiredGravityPlanner.plan(snapshot, Set.copyOf(enabledItemIds), maximumItems);
        if (plan.bounded()) {
            LOGGER.warn(
                    "Gravity settle bounded: room={}, candidates={}, processed={}",
                    room.getId(),
                    plan.candidateCount(),
                    maximumItems);
        }

        List<WiredMovementsComposer.MovementData> movements = new ArrayList<>();
        long retryAfterMs = 0L;
        for (WiredGravityPlanner.Fall fall : plan.falls()) {
            if (!sameGeneration(expectedGeneration)) {
                break;
            }
            WiredGravityPlanner.FurnitureSnapshot expected = fall.item();
            HabboItem item = room.getHabboItem(expected.id());
            if (!matchesSnapshot(item, expected) || !enabledItemIds.contains(expected.id())) {
                retryAfterMs = Math.max(retryAfterMs, retryDelayMs);
                continue;
            }

            long now = clock.getAsLong();
            Long activeUntil = movingUntil.get(item.getId());
            if (activeUntil != null && activeUntil > now) {
                retryAfterMs = Math.max(retryAfterMs, activeUntil - now);
                continue;
            }
            movingUntil.remove(item.getId());

            RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
            if (tile == null) {
                continue;
            }
            List<RiderSnapshot> riders = snapshotRiders(item);
            int durationMs = fallDuration(fall.distance());
            FurnitureMovementError result =
                    room.moveFurniTo(item, tile, item.getRotation(), fall.targetZ(), null, false, false);
            if (result != FurnitureMovementError.NONE) {
                retryAfterMs = Math.max(retryAfterMs, retryDelayMs);
                continue;
            }

            movingUntil.put(item.getId(), now + durationMs);
            movements.add(WiredMovementsComposer.furniMovement(
                    item.getId(),
                    expected.x(),
                    expected.y(),
                    item.getX(),
                    item.getY(),
                    expected.z(),
                    item.getZ(),
                    item.getRotation(),
                    durationMs));
            appendRiderMovements(item, riders, fall.distance(), durationMs, movements);
            retryAfterMs = Math.max(retryAfterMs, durationMs);
        }

        if (!movements.isEmpty()) {
            room.sendComposer(new WiredMovementsComposer(movements).compose());
        }
        return retryAfterMs;
    }

    private WiredGravityPlanner.Snapshot snapshotRoom() {
        RoomLayout layout = room.getLayout();
        if (layout == null) {
            return new WiredGravityPlanner.Snapshot(List.of(), Map.of());
        }

        List<WiredGravityPlanner.FurnitureSnapshot> furniture = new ArrayList<>();
        Map<WiredGravityPlanner.TilePosition, Double> floorHeights = new HashMap<>();
        for (HabboItem item : new ArrayList<>(room.getFloorItems())) {
            if (!isEligibleFloorItem(item) || item.getRoomId() != room.getId()) {
                continue;
            }
            RoomTile origin = layout.getTile(item.getX(), item.getY());
            if (origin == null) {
                continue;
            }
            Set<WiredGravityPlanner.TilePosition> footprint = new LinkedHashSet<>();
            for (RoomTile tile : layout.getTilesAt(
                    origin, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), item.getRotation())) {
                if (tile == null || tile.state == RoomTileState.INVALID) {
                    footprint.clear();
                    break;
                }
                WiredGravityPlanner.TilePosition position = new WiredGravityPlanner.TilePosition(tile.x, tile.y);
                footprint.add(position);
                floorHeights.put(position, (double) layout.getHeightAtSquare(tile.x, tile.y));
            }
            furniture.add(new WiredGravityPlanner.FurnitureSnapshot(
                    item.getId(),
                    item.getX(),
                    item.getY(),
                    item.getZ(),
                    item.getRotation(),
                    Item.getCurrentHeight(item),
                    footprint));
        }
        return new WiredGravityPlanner.Snapshot(furniture, floorHeights);
    }

    private List<RiderSnapshot> snapshotRiders(HabboItem item) {
        RoomLayout layout = room.getLayout();
        RoomTile origin = layout == null ? null : layout.getTile(item.getX(), item.getY());
        if (origin == null) {
            return List.of();
        }

        List<RiderSnapshot> riders = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (RoomTile tile : layout.getTilesAt(
                origin, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), item.getRotation())) {
            for (RoomUnit unit : room.getRoomUnits(tile)) {
                if (unit != null
                        && unit.isInRoom()
                        && seen.add(unit.getId())
                        && unit.getCurrentLocation() == tile
                        && room.getTopItemAt(tile.x, tile.y) == item) {
                    riders.add(new RiderSnapshot(unit, tile, unit.getZ()));
                }
            }
        }
        return riders;
    }

    private void appendRiderMovements(
            HabboItem item,
            List<RiderSnapshot> riders,
            double dropDistance,
            int durationMs,
            List<WiredMovementsComposer.MovementData> movements) {
        for (RiderSnapshot rider : riders) {
            RoomUnit unit = rider.unit();
            RoomTile tile = rider.tile();
            if (unit == null
                    || !unit.isInRoom()
                    || unit.getCurrentLocation() != tile
                    || room.getTopItemAt(tile.x, tile.y) != item) {
                continue;
            }
            double targetZ = rider.oldZ() - dropDistance;
            unit.removeStatus(RoomUnitStatus.MOVE);
            unit.setPath(new LinkedList<>());
            unit.setZ(targetZ);
            unit.setPreviousLocation(tile);
            unit.setPreviousLocationZ(targetZ);
            unit.setLastRollerTime(clock.getAsLong());
            unit.statusUpdate(false);
            movements.add(WiredMovementsComposer.userSlideMovement(
                    unit.getId(),
                    tile.x,
                    tile.y,
                    tile.x,
                    tile.y,
                    rider.oldZ(),
                    targetZ,
                    unit.getBodyRotation() == null ? 0 : unit.getBodyRotation().getValue(),
                    unit.getHeadRotation() == null ? 0 : unit.getHeadRotation().getValue(),
                    durationMs));
        }
    }

    private boolean sameGeneration(long expectedGeneration) {
        synchronized (taskLock) {
            return generation == expectedGeneration;
        }
    }

    private static boolean matchesSnapshot(HabboItem item, WiredGravityPlanner.FurnitureSnapshot expected) {
        return isEligibleFloorItem(item)
                && item.getX() == expected.x()
                && item.getY() == expected.y()
                && item.getRotation() == expected.rotation()
                && Math.abs(item.getZ() - expected.z()) <= WiredGravityPlanner.HEIGHT_EPSILON;
    }

    private static boolean isEligibleFloorItem(HabboItem item) {
        return item != null && item.getBaseItem() != null && item.getBaseItem().getType() == FurnitureType.FLOOR;
    }

    private static int fallDuration(double distance) {
        int duration = 180 + (int) Math.round(140D * Math.sqrt(Math.max(0D, distance)));
        return Math.max(220, Math.min(900, duration));
    }

    private static int configuredInt(String key, int fallback, int minimum, int maximum) {
        int configured = WiredPlatform.configuration() == null
                ? fallback
                : WiredPlatform.configuration().getInt(key, fallback);
        return Math.max(minimum, Math.min(maximum, configured));
    }

    @FunctionalInterface
    interface Scheduler {
        Cancellable schedule(Runnable task, long delayMs);
    }

    @FunctionalInterface
    interface Cancellable {
        boolean cancel();
    }

    private record RiderSnapshot(RoomUnit unit, RoomTile tile, double oldZ) {}
}
