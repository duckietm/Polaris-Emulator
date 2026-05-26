package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionStackWalkHelper;
import com.eu.habbo.habbohotel.items.interactions.InteractionTileWalkMagic;
import com.eu.habbo.habbohotel.items.interactions.interfaces.ConditionalGate;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WiredUserMovementHelper {
    public static final int DEFAULT_ANIMATION_DURATION = WiredMovementsComposer.DEFAULT_DURATION;
    private static final int SUPPRESS_NEXT_WALK_WINDOW_MS = 250;
    private static final int STATUS_SUPPRESSION_GRACE_MS = 250;
    private static final String SUPPRESS_NEXT_WALK_CACHE_KEY = "wired_suppress_next_walk_until";

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredUserMovementHelper.class);
    private static final ThreadLocal<Set<Integer>> SUPPRESSED_STATUS_ROOM_UNIT_IDS = new ThreadLocal<>();
    private static final ConcurrentHashMap<Integer, Long> SUPPRESSED_STATUS_COMPOSER_UNTIL = new ConcurrentHashMap<>();

    private WiredUserMovementHelper() {
    }

    public static boolean moveUser(Room room, RoomUnit roomUnit, RoomTile targetTile, double targetZ, int duration) {
        return moveUser(room, roomUnit, targetTile, targetZ, roomUnit == null ? null : roomUnit.getBodyRotation(),
                roomUnit == null ? null : roomUnit.getHeadRotation(), duration, false, WiredMovementPhysics.NONE);
    }

    public static boolean moveUser(Room room, RoomUnit roomUnit, RoomTile targetTile, double targetZ, RoomUserRotation bodyRotation, RoomUserRotation headRotation, int duration) {
        return moveUser(room, roomUnit, targetTile, targetZ, bodyRotation, headRotation, duration, false, WiredMovementPhysics.NONE);
    }

    public static boolean moveUser(Room room, RoomUnit roomUnit, RoomTile targetTile, double targetZ, RoomUserRotation bodyRotation, RoomUserRotation headRotation, int duration, boolean noAnimation) {
        return moveUser(room, roomUnit, targetTile, targetZ, bodyRotation, headRotation, duration, noAnimation, WiredMovementPhysics.NONE);
    }

    public static boolean moveUser(Room room, RoomUnit roomUnit, RoomTile targetTile, double targetZ, RoomUserRotation bodyRotation, RoomUserRotation headRotation, int duration, boolean noAnimation, WiredMovementPhysics movementPhysics) {
        if (room == null || roomUnit == null || targetTile == null || room.getLayout() == null) {
            return false;
        }

        RoomTile oldLocation = roomUnit.getCurrentLocation();
        WiredMovementPhysics resolvedMovementPhysics = movementPhysics == null ? WiredMovementPhysics.NONE : movementPhysics;

        if (oldLocation == null || !canMoveTo(room, roomUnit, targetTile, resolvedMovementPhysics)) {
            return false;
        }

        RoomUserRotation resolvedBodyRotation = bodyRotation == null ? roomUnit.getBodyRotation() : bodyRotation;
        RoomUserRotation resolvedHeadRotation = headRotation == null ? roomUnit.getHeadRotation() : headRotation;

        if (noAnimation) {
            return moveUserInstant(
                    room,
                    roomUnit,
                    oldLocation,
                    targetTile,
                    roomUnit.getZ(),
                    targetZ,
                    resolvedBodyRotation,
                    resolvedHeadRotation,
                    room.getTopItemAt(oldLocation.x, oldLocation.y),
                    resolveEnteredItem(room, targetTile),
                    room.getHabbo(roomUnit));
        }

        double oldZ = roomUnit.getZ();
        HabboItem oldTopItem = room.getTopItemAt(oldLocation.x, oldLocation.y);
        HabboItem newTopItem = resolveEnteredItem(room, targetTile);
        Habbo habbo = room.getHabbo(roomUnit);

        int animationDuration = (duration > 0) ? duration : DEFAULT_ANIMATION_DURATION;

        runWithSuppressedStatusUpdates(Collections.singletonList(roomUnit), () -> {
            roomUnit.removeStatus(RoomUnitStatus.MOVE);
            roomUnit.setZ(targetZ);
            roomUnit.setLocation(targetTile);
            roomUnit.setPath(new LinkedList<>());
            roomUnit.setBodyRotation(resolvedBodyRotation);
            roomUnit.setHeadRotation(resolvedHeadRotation);
            roomUnit.resetIdleTimer();

            if (habbo != null) {
                THashSet<Habbo> movedHabbos = new THashSet<>();
                movedHabbos.add(habbo);
                room.updateHabbosAt(targetTile.x, targetTile.y, movedHabbos);
            }

            roomUnit.statusUpdate(false);
        });

        List<WiredMovementsComposer.MovementData> movements = new ArrayList<>();
        movements.add(WiredMovementsComposer.userSlideMovement(
                roomUnit.getId(),
                oldLocation.x,
                oldLocation.y,
                targetTile.x,
                targetTile.y,
                oldZ,
                roomUnit.getZ(),
                resolvedBodyRotation.getValue(),
                resolvedHeadRotation.getValue(),
                animationDuration));
        suppressStatusComposer(roomUnit, animationDuration);
        room.sendComposer(new WiredMovementsComposer(movements).compose());

        scheduleTileCallbacks(room, roomUnit, oldLocation, targetTile, oldTopItem, newTopItem, animationDuration);
        scheduleFinalStatusSync(room, roomUnit, targetTile, animationDuration);
        schedulePostureSync(room, roomUnit, targetTile, animationDuration);
        return true;
    }

    public static void suppressNextWalkCommand(RoomUnit roomUnit) {
        if (roomUnit == null || roomUnit.getCacheable() == null) {
            return;
        }

        roomUnit.getCacheable().put(SUPPRESS_NEXT_WALK_CACHE_KEY, System.currentTimeMillis() + SUPPRESS_NEXT_WALK_WINDOW_MS);
    }

    public static boolean consumeSuppressedWalkCommand(RoomUnit roomUnit) {
        if (roomUnit == null || roomUnit.getCacheable() == null) {
            return false;
        }

        Object value = roomUnit.getCacheable().remove(SUPPRESS_NEXT_WALK_CACHE_KEY);

        if (!(value instanceof Long)) {
            return false;
        }

        return ((Long) value) >= System.currentTimeMillis();
    }

    private static boolean moveUserInstant(Room room, RoomUnit roomUnit, RoomTile oldLocation, RoomTile targetTile, double oldZ, double targetZ,
                                           RoomUserRotation bodyRotation, RoomUserRotation headRotation, HabboItem oldTopItem,
                                           HabboItem newTopItem, Habbo habbo) {
        suppressNextWalkCommand(roomUnit);
        roomUnit.removeStatus(RoomUnitStatus.MOVE);
        roomUnit.setPath(new LinkedList<>());
        roomUnit.setBodyRotation(bodyRotation);
        roomUnit.setHeadRotation(headRotation);
        roomUnit.setCurrentLocation(targetTile);
        roomUnit.setGoalLocation(targetTile);
        roomUnit.setZ(targetZ);
        roomUnit.setPreviousLocation(oldLocation);
        roomUnit.setPreviousLocationZ(oldZ);
        roomUnit.resetIdleTimer();
        roomUnit.statusUpdate(true);

        if (habbo != null) {
            THashSet<Habbo> movedHabbos = new THashSet<>();
            movedHabbos.add(habbo);
            room.updateHabbosAt(targetTile.x, targetTile.y, movedHabbos);
        } else {
            switch (roomUnit.getRoomUnitType()) {
                case BOT -> room.updateBotsAt(targetTile.x, targetTile.y);
                case PET -> room.updatePetsAt(targetTile.x, targetTile.y);
            }
        }

        processTileCallbacks(room, roomUnit, oldLocation, targetTile, oldTopItem, newTopItem);
        clearStatusComposerSuppression(roomUnit);
        room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
        return true;
    }

    public static boolean updateUserDirection(Room room, RoomUnit roomUnit, RoomUserRotation bodyRotation, RoomUserRotation headRotation) {
        if (room == null || roomUnit == null) {
            return false;
        }

        RoomUserRotation resolvedBodyRotation = bodyRotation == null ? roomUnit.getBodyRotation() : bodyRotation;
        RoomUserRotation resolvedHeadRotation = headRotation == null ? roomUnit.getHeadRotation() : headRotation;

        roomUnit.setBodyRotation(resolvedBodyRotation);
        roomUnit.setHeadRotation(resolvedHeadRotation);
        room.sendComposer(new WiredMovementsComposer(Collections.singletonList(
                WiredMovementsComposer.userDirectionUpdate(
                        roomUnit.getId(),
                        resolvedHeadRotation.getValue(),
                        resolvedBodyRotation.getValue()))).compose());
        return true;
    }

    public static boolean shouldSuppressStatusUpdate(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return false;
        }

        Set<Integer> suppressedRoomUnitIds = SUPPRESSED_STATUS_ROOM_UNIT_IDS.get();
        return suppressedRoomUnitIds != null && suppressedRoomUnitIds.contains(roomUnit.getId());
    }

    public static boolean shouldSuppressStatusComposer(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return false;
        }

        Long suppressedUntil = SUPPRESSED_STATUS_COMPOSER_UNTIL.get(roomUnit.getId());

        if (suppressedUntil == null) {
            return false;
        }

        if (suppressedUntil <= System.currentTimeMillis()) {
            SUPPRESSED_STATUS_COMPOSER_UNTIL.remove(roomUnit.getId(), suppressedUntil);
            return false;
        }

        return true;
    }

    public static void clearStatusComposerSuppression(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return;
        }

        SUPPRESSED_STATUS_COMPOSER_UNTIL.remove(roomUnit.getId());
    }

    public static boolean canMoveTo(Room room, RoomUnit roomUnit, RoomTile targetTile, WiredMovementPhysics movementPhysics) {
        if (room == null || roomUnit == null || targetTile == null) {
            return false;
        }

        WiredMovementPhysics resolvedMovementPhysics = movementPhysics == null ? WiredMovementPhysics.NONE : movementPhysics;

        if (targetTile.state == null || targetTile.state == com.eu.habbo.habbohotel.rooms.RoomTileState.INVALID) {
            return false;
        }

        if (targetTile.state == com.eu.habbo.habbohotel.rooms.RoomTileState.BLOCKED
                && !canBypassBlockedTile(room, targetTile, resolvedMovementPhysics)) {
            return false;
        }

        if (!room.getLayout().tileWalkable(targetTile.x, targetTile.y)
                && !room.canSitOrLayAt(targetTile.x, targetTile.y)
                && !canBypassBlockedTile(room, targetTile, resolvedMovementPhysics)) {
            return false;
        }

        return !hasBlockingUnits(room, roomUnit, targetTile, resolvedMovementPhysics);
    }

    private static boolean hasBlockingUnits(Room room, RoomUnit roomUnit, RoomTile targetTile, WiredMovementPhysics movementPhysics) {
        Collection<RoomUnit> units = room.getRoomUnitsAt(targetTile);

        if (units == null || units.isEmpty()) {
            return false;
        }

        for (RoomUnit targetUnit : units) {
            if (targetUnit != null
                    && targetUnit != roomUnit
                    && !movementPhysics.shouldIgnoreUser(targetUnit)) {
                return true;
            }
        }

        return false;
    }

    private static boolean canBypassBlockedTile(Room room, RoomTile targetTile, WiredMovementPhysics movementPhysics) {
        if (room == null || targetTile == null || movementPhysics == null || !movementPhysics.isActive()) {
            return false;
        }

        Collection<HabboItem> items = room.getItemsAt(targetTile);
        if (items == null || items.isEmpty()) {
            return false;
        }

        boolean hasIgnoredFurni = false;

        for (HabboItem item : items) {
            if (item == null) {
                continue;
            }

            if (movementPhysics.isBlockingFurni(item)) {
                return false;
            }

            if (movementPhysics.shouldIgnoreFurni(item)) {
                hasIgnoredFurni = true;
                continue;
            }

            if (!item.isWalkable()
                    && !item.getBaseItem().allowSit()
                    && !item.getBaseItem().allowLay()) {
                return false;
            }
        }

        return hasIgnoredFurni;
    }

    private static void scheduleTileCallbacks(Room room, RoomUnit roomUnit, RoomTile oldLocation, RoomTile targetTile, HabboItem oldTopItem, HabboItem newTopItem, int delay) {
        Emulator.getThreading().run(() -> {
            processTileCallbacks(room, roomUnit, oldLocation, targetTile, oldTopItem, newTopItem);
        }, Math.max(delay, 1));
    }

    private static void processTileCallbacks(Room room, RoomUnit roomUnit, RoomTile oldLocation, RoomTile targetTile, HabboItem oldTopItem, HabboItem newTopItem) {
        if (room == null || !room.isLoaded() || roomUnit == null || roomUnit.getCurrentLocation() == null) {
            return;
        }

        if (roomUnit.getCurrentLocation().x != targetTile.x || roomUnit.getCurrentLocation().y != targetTile.y) {
            return;
        }

        HabboItem resolvedNewTopItem = resolveEnteredItem(room, targetTile);
        if (resolvedNewTopItem == null) {
            resolvedNewTopItem = room.getTopItemAt(targetTile.x, targetTile.y);
        }
        if (resolvedNewTopItem == null) {
            resolvedNewTopItem = newTopItem;
        }

        if (oldTopItem != null && (oldTopItem != resolvedNewTopItem || !occupiesTile(oldTopItem, targetTile))) {
            try {
                oldTopItem.onWalkOff(roomUnit, room, new Object[]{oldLocation, targetTile});
            } catch (Exception exception) {
                LOGGER.error("Failed to process wired user walk off callback", exception);
            }
        }

        for (HabboItem additionalOldItem : resolveAdditionalTileItems(room, oldLocation, oldTopItem)) {
            if (additionalOldItem == resolvedNewTopItem && occupiesTile(additionalOldItem, targetTile)) {
                continue;
            }

            try {
                additionalOldItem.onWalkOff(roomUnit, room, new Object[]{oldLocation, targetTile});
            } catch (Exception exception) {
                LOGGER.error("Failed to process additional wired user walk off callback", exception);
            }
        }

        if (resolvedNewTopItem != null) {
            try {
                if (resolvedNewTopItem != oldTopItem || !occupiesTile(resolvedNewTopItem, oldLocation)) {
                    if (!resolvedNewTopItem.canWalkOn(roomUnit, room, null)) {
                        if (resolvedNewTopItem instanceof ConditionalGate) {
                            roomUnit.setLocation(oldLocation);
                            roomUnit.setZ(oldLocation.getStackHeight());
                            roomUnit.setPreviousLocation(oldLocation);
                            roomUnit.setPreviousLocationZ(oldLocation.getStackHeight());
                            room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
                            return;
                        }
                    } else {
                        resolvedNewTopItem.onWalkOn(roomUnit, room, new Object[]{oldLocation, targetTile});
                    }
                } else {
                    resolvedNewTopItem.onWalk(roomUnit, room, new Object[]{oldLocation, targetTile});
                }
            } catch (Exception exception) {
                LOGGER.error("Failed to process wired user walk on callback", exception);
            }
        }

        for (HabboItem additionalNewItem : resolveAdditionalTileItems(room, targetTile, resolvedNewTopItem)) {
            try {
                if (occupiesTile(additionalNewItem, oldLocation)) {
                    additionalNewItem.onWalk(roomUnit, room, new Object[]{oldLocation, targetTile});
                } else {
                    additionalNewItem.onWalkOn(roomUnit, room, new Object[]{oldLocation, targetTile});
                }
            } catch (Exception exception) {
                LOGGER.error("Failed to process additional wired user walk on callback", exception);
            }
        }
    }

    private static HabboItem resolveEnteredItem(Room room, RoomTile tile) {
        if (room == null || tile == null || room.getItemManager() == null) {
            return null;
        }

        if (room.canSitAt(tile.x, tile.y)) {
            HabboItem tallestChair = room.getTallestChair(tile);
            if (tallestChair != null) {
                return tallestChair;
            }
        }

        HabboItem candidate = null;

        for (HabboItem item : room.getItemsAt(tile)) {
            if (item == null || !occupiesTile(item, tile)) {
                continue;
            }

            boolean preferred = item instanceof ConditionalGate
                    || item.isWalkable()
                    || item.getBaseItem().allowWalk()
                    || item.getBaseItem().allowSit()
                    || item.getBaseItem().allowLay();

            if (!preferred) {
                continue;
            }

            if (candidate == null || item.getZ() >= candidate.getZ()) {
                candidate = item;
            }
        }

        if (candidate != null) {
            return candidate;
        }

        return room.getTopItemAt(tile.x, tile.y);
    }

    public static double resolveUserTargetZ(Room room, RoomTile targetTile) {
        if (room == null || targetTile == null || room.getLayout() == null) {
            return 0;
        }

        HabboItem targetItem = resolveEnteredItem(room, targetTile);
        double targetZ = room.getLayout().getHeightAtSquare(targetTile.x, targetTile.y);

        if (targetItem != null) {
            targetZ = targetItem.getZ();

            if (!targetItem.getBaseItem().allowSit() && !targetItem.getBaseItem().allowLay()) {
                targetZ += Item.getCurrentHeight(targetItem);
            }
        }

        for (HabboItem item : room.getItemsAt(targetTile)) {
            if (item instanceof InteractionTileWalkMagic || item instanceof InteractionStackWalkHelper) {
                targetZ = item.getZ();
                break;
            }
        }

        return targetZ;
    }

    private static boolean occupiesTile(HabboItem item, RoomTile tile) {
        if (item == null || tile == null || item.getBaseItem() == null) {
            return false;
        }

        return RoomLayout.pointInSquare(
                item.getX(),
                item.getY(),
                item.getX() + item.getBaseItem().getWidth() - 1,
                item.getY() + item.getBaseItem().getLength() - 1,
                tile.x,
                tile.y);
    }

    private static List<HabboItem> resolveAdditionalTileItems(Room room, RoomTile tile, HabboItem primaryItem) {
        if (room == null || tile == null) {
            return Collections.emptyList();
        }

        List<HabboItem> items = new ArrayList<>();

        for (HabboItem item : room.getItemsAt(tile)) {
            if (item == null || item == primaryItem || !occupiesTile(item, tile)) {
                continue;
            }

            items.add(item);
        }

        items.sort(Comparator
                .comparingDouble((HabboItem item) -> item.getZ() + Item.getCurrentHeight(item))
                .thenComparingInt(HabboItem::getId)
                .reversed());
        return items;
    }

    private static void schedulePostureSync(Room room, RoomUnit roomUnit, RoomTile targetTile, int delay) {
        if (!roomUnit.hasStatus(RoomUnitStatus.SIT) && !roomUnit.hasStatus(RoomUnitStatus.LAY)) {
            return;
        }

        Emulator.getThreading().run(() -> {
            if (room == null || !room.isLoaded() || roomUnit == null || roomUnit.getCurrentLocation() == null) {
                return;
            }

            if (roomUnit.getCurrentLocation().x != targetTile.x || roomUnit.getCurrentLocation().y != targetTile.y) {
                return;
            }

            room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
        }, delay + STATUS_SUPPRESSION_GRACE_MS + 25);
    }

    private static void scheduleFinalStatusSync(Room room, RoomUnit roomUnit, RoomTile targetTile, int delay) {
        if (room == null || roomUnit == null || targetTile == null) {
            return;
        }

        Emulator.getThreading().run(() -> {
            if (room == null || !room.isLoaded() || roomUnit == null || roomUnit.getCurrentLocation() == null) {
                return;
            }

            if (roomUnit.isWalking()
                    || roomUnit.getCurrentLocation().x != targetTile.x
                    || roomUnit.getCurrentLocation().y != targetTile.y) {
                return;
            }

            clearStatusComposerSuppression(roomUnit);
            roomUnit.setPreviousLocation(roomUnit.getCurrentLocation());
            roomUnit.setPreviousLocationZ(roomUnit.getZ());
            room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
        }, Math.max(delay, 1) + 25);
    }

    private static void suppressStatusComposer(RoomUnit roomUnit, int duration) {
        if (roomUnit == null) {
            return;
        }

        long suppressedUntil = System.currentTimeMillis() + Math.max(duration, InteractionRoller.DELAY) + STATUS_SUPPRESSION_GRACE_MS;
        SUPPRESSED_STATUS_COMPOSER_UNTIL.put(roomUnit.getId(), suppressedUntil);
    }

    private static void runWithSuppressedStatusUpdates(Collection<RoomUnit> roomUnits, Runnable action) {
        if (action == null) {
            return;
        }

        Set<Integer> previousSuppressedRoomUnitIds = SUPPRESSED_STATUS_ROOM_UNIT_IDS.get();
        HashSet<Integer> suppressedRoomUnitIds = previousSuppressedRoomUnitIds == null
                ? new HashSet<>()
                : new HashSet<>(previousSuppressedRoomUnitIds);

        if (roomUnits != null) {
            for (RoomUnit roomUnit : roomUnits) {
                if (roomUnit != null) {
                    suppressedRoomUnitIds.add(roomUnit.getId());
                }
            }
        }

        if (suppressedRoomUnitIds.isEmpty()) {
            action.run();
            return;
        }

        SUPPRESSED_STATUS_ROOM_UNIT_IDS.set(suppressedRoomUnitIds);

        try {
            action.run();
        } finally {
            if (previousSuppressedRoomUnitIds == null || previousSuppressedRoomUnitIds.isEmpty()) {
                SUPPRESSED_STATUS_ROOM_UNIT_IDS.remove();
            } else {
                SUPPRESSED_STATUS_ROOM_UNIT_IDS.set(previousSuppressedRoomUnitIds);
            }
        }
    }

    public static void cleanupRoomUnit(RoomUnit roomUnit) {
        if (roomUnit != null) {
            SUPPRESSED_STATUS_COMPOSER_UNTIL.remove(roomUnit.getId());
        }
    }
}
