package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraAnimationTime;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraMoveCarryUsers;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraMovePhysics;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraMoveNoAnimation;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemOnRollerComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import gnu.trove.set.hash.THashSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WiredMoveCarryHelper {
    private static final double DIRECT_HEIGHT_TOLERANCE = 0.1D;
    private static final int STATUS_SUPPRESSION_GRACE_MS = 250;
    private static final long USER_FOLLOWER_TTL_MS = 10000L;
    private static final ThreadLocal<Set<Integer>> SUPPRESSED_STATUS_ROOM_UNIT_IDS = new ThreadLocal<>();
    private static final ThreadLocal<List<WiredMovementsComposer.MovementData>> COLLECTED_MOVEMENTS = new ThreadLocal<>();
    private static final ThreadLocal<Integer> MOVEMENT_COLLECTION_DEPTH = new ThreadLocal<>();
    private static final ConcurrentHashMap<Integer, Long> SUPPRESSED_STATUS_COMPOSER_UNTIL = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, UserFollowEntry>> ACTIVE_USER_FOLLOWERS = new ConcurrentHashMap<>();

    private WiredMoveCarryHelper() {
    }

    public static FurnitureMovementError getMovementError(Room room, HabboItem stackItem, HabboItem movingItem, RoomTile targetTile, int rotation, WiredContext ctx) {
        if (room == null || movingItem == null || targetTile == null) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        if (!hasMovementBehaviorExtra(room, stackItem)) {
            return room.furnitureFitsAt(targetTile, movingItem, rotation, true);
        }

        CarryContext carryContext = prepareCarryContext(room, stackItem, movingItem, ctx);
        WiredMovementPhysics movementPhysics = getMovementPhysics(room, stackItem, movingItem, ctx);
        FurnitureMovementError movementError = room.furnitureFitsAtWithPhysics(targetTile, movingItem, rotation, false, movementPhysics);

        if (movementError != FurnitureMovementError.NONE) {
            return movementError;
        }

        if (!carryContext.active) {
            return room.furnitureFitsAtWithPhysics(targetTile, movingItem, rotation, true, movementPhysics);
        }

        return getBlockingUnitError(room, movingItem, targetTile, rotation, carryContext, movementPhysics);
    }

    public static FurnitureMovementError moveFurni(Room room, HabboItem stackItem, HabboItem movingItem, RoomTile targetTile, int rotation, Habbo actor, boolean sendUpdates, WiredContext ctx) {
        return moveFurni(room, stackItem, movingItem, targetTile, rotation, null, actor, sendUpdates, ctx, null, null, WiredMovementsComposer.FURNI_ANCHOR_NONE, 0);
    }

    public static FurnitureMovementError moveFurni(Room room, HabboItem stackItem, HabboItem movingItem, RoomTile targetTile, int rotation, Double z, Habbo actor, boolean sendUpdates, WiredContext ctx) {
        return moveFurni(room, stackItem, movingItem, targetTile, rotation, z, actor, sendUpdates, ctx, null, null, WiredMovementsComposer.FURNI_ANCHOR_NONE, 0);
    }

    public static FurnitureMovementError moveFurni(Room room, HabboItem stackItem, HabboItem movingItem, RoomTile targetTile, int rotation, Double z, Habbo actor, boolean sendUpdates, WiredContext ctx, Integer animationDurationOverride) {
        return moveFurni(room, stackItem, movingItem, targetTile, rotation, z, actor, sendUpdates, ctx, animationDurationOverride, null, WiredMovementsComposer.FURNI_ANCHOR_NONE, 0);
    }

    public static FurnitureMovementError moveFurni(Room room, HabboItem stackItem, HabboItem movingItem, RoomTile targetTile, int rotation, Double z, Habbo actor, boolean sendUpdates, WiredContext ctx, Integer animationDurationOverride, Integer animationElapsedOverride) {
        return moveFurni(room, stackItem, movingItem, targetTile, rotation, z, actor, sendUpdates, ctx, animationDurationOverride, animationElapsedOverride, WiredMovementsComposer.FURNI_ANCHOR_NONE, 0);
    }

    public static FurnitureMovementError moveFurni(Room room, HabboItem stackItem, HabboItem movingItem, RoomTile targetTile, int rotation, Double z, Habbo actor, boolean sendUpdates, WiredContext ctx, Integer animationDurationOverride, Integer animationElapsedOverride, int anchorType, int anchorId) {
        if (room == null || movingItem == null || targetTile == null) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        if (!hasMovementBehaviorExtra(room, stackItem)) {
            return moveFurniLegacy(room, movingItem, targetTile, rotation, z, actor, sendUpdates);
        }

        RoomTile oldLocation = room.getLayout() == null ? null : room.getLayout().getTile(movingItem.getX(), movingItem.getY());
        double oldZ = movingItem.getZ();
        CarryContext carryContext = prepareCarryContext(room, stackItem, movingItem, ctx);
        WiredMovementPhysics movementPhysics = getMovementPhysics(room, stackItem, movingItem, ctx);
        FurnitureMovementError movementError = room.furnitureFitsAtWithPhysics(targetTile, movingItem, rotation, false, movementPhysics);

        if (movementError != FurnitureMovementError.NONE) {
            return movementError;
        }

        if (carryContext.active) {
            movementError = getBlockingUnitError(room, movingItem, targetTile, rotation, carryContext, movementPhysics);

            if (movementError != FurnitureMovementError.NONE) {
                return movementError;
            }
        } else {
            movementError = room.furnitureFitsAtWithPhysics(targetTile, movingItem, rotation, true, movementPhysics);

            if (movementError != FurnitureMovementError.NONE) {
                return movementError;
            }
        }

        boolean useWiredMovements = !hasNoAnimationExtra(room, stackItem);
        int animationDuration = animationDurationOverride != null
                ? Math.max(50, animationDurationOverride)
                : getAnimationDuration(room, stackItem, WiredMovementsComposer.DEFAULT_DURATION);
        Set<Integer> previousSuppressedRoomUnitIds = SUPPRESSED_STATUS_ROOM_UNIT_IDS.get();

        if (carryContext.active) {
            HashSet<Integer> suppressedRoomUnitIds = previousSuppressedRoomUnitIds == null
                    ? new HashSet<>()
                    : new HashSet<>(previousSuppressedRoomUnitIds);
            suppressedRoomUnitIds.addAll(carryContext.carriedUserIds);
            SUPPRESSED_STATUS_ROOM_UNIT_IDS.set(suppressedRoomUnitIds);
        }

        FurnitureMovementError result;
        Double targetZ = z;

        if (targetZ == null && movementPhysics.isKeepAltitude()) {
            targetZ = oldZ;
        }

        try {
            result = (targetZ == null)
                    ? room.moveFurniToWithPhysics(movingItem, targetTile, rotation, actor, !useWiredMovements, false, movementPhysics)
                    : room.moveFurniToWithPhysics(movingItem, targetTile, rotation, targetZ, actor, !useWiredMovements, false, movementPhysics);
        } finally {
            if (carryContext.active) {
                if (previousSuppressedRoomUnitIds == null || previousSuppressedRoomUnitIds.isEmpty()) {
                    SUPPRESSED_STATUS_ROOM_UNIT_IDS.remove();
                } else {
                    SUPPRESSED_STATUS_ROOM_UNIT_IDS.set(previousSuppressedRoomUnitIds);
                }
            }
        }

        if (result == FurnitureMovementError.NONE) {
            if (!useWiredMovements) {
                applyInstantCarryState(room, movingItem, targetTile, rotation, carryContext);
            } else if (oldLocation != null) {
                sendAnimatedMove(room, movingItem, oldLocation, oldZ, targetTile, rotation, carryContext, animationDuration, (animationElapsedOverride != null) ? Math.max(0, animationElapsedOverride) : 0, anchorType, anchorId);
            }
        }

        return result;
    }

    private static FurnitureMovementError moveFurniLegacy(Room room, HabboItem movingItem, RoomTile targetTile, int rotation, Double z, Habbo actor, boolean sendUpdates) {
        if (room == null || movingItem == null || targetTile == null) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        RoomTile oldLocation = room.getLayout() == null ? null : room.getLayout().getTile(movingItem.getX(), movingItem.getY());
        double oldZ = movingItem.getZ();

        FurnitureMovementError result = (z == null)
                ? room.moveFurniTo(movingItem, targetTile, rotation, actor, sendUpdates)
                : room.moveFurniTo(movingItem, targetTile, rotation, z, actor, sendUpdates, false);

        if (result == FurnitureMovementError.NONE
                && !sendUpdates
                && oldLocation != null
                && (oldLocation.x != targetTile.x || oldLocation.y != targetTile.y || Double.compare(oldZ, movingItem.getZ()) != 0)) {
            List<WiredMovementsComposer.MovementData> collectedMovements = COLLECTED_MOVEMENTS.get();

            if (collectedMovements != null) {
                collectedMovements.add(WiredMovementsComposer.furniMovement(
                        movingItem.getId(),
                        oldLocation.x,
                        oldLocation.y,
                        targetTile.x,
                        targetTile.y,
                        oldZ,
                        movingItem.getZ(),
                        movingItem.getRotation(),
                        WiredMovementsComposer.DEFAULT_DURATION,
                        0,
                        WiredMovementsComposer.FURNI_ANCHOR_NONE,
                        0));
            } else {
                room.sendComposer(new FloorItemOnRollerComposer(movingItem, null, oldLocation, oldZ, targetTile, movingItem.getZ(), 0, room).compose());
            }
        }

        return result;
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

    public static void beginMovementCollection() {
        Integer currentDepth = MOVEMENT_COLLECTION_DEPTH.get();

        if (currentDepth == null || currentDepth <= 0) {
            COLLECTED_MOVEMENTS.set(new ArrayList<>());
            MOVEMENT_COLLECTION_DEPTH.set(1);
            return;
        }

        MOVEMENT_COLLECTION_DEPTH.set(currentDepth + 1);
    }

    public static ServerMessage finishMovementCollection() {
        Integer currentDepth = MOVEMENT_COLLECTION_DEPTH.get();

        if (currentDepth != null && currentDepth > 1) {
            MOVEMENT_COLLECTION_DEPTH.set(currentDepth - 1);
            return null;
        }

        List<WiredMovementsComposer.MovementData> movements = COLLECTED_MOVEMENTS.get();
        COLLECTED_MOVEMENTS.remove();
        MOVEMENT_COLLECTION_DEPTH.remove();

        if (movements == null || movements.isEmpty()) {
            return null;
        }

        return new WiredMovementsComposer(movements).compose();
    }

    public static void registerUserFollower(Room room, HabboItem stackItem, HabboItem movingItem, RoomUnit targetUnit, Double zOverride, WiredContext ctx) {
        if (room == null || stackItem == null || movingItem == null || targetUnit == null) {
            return;
        }

        ACTIVE_USER_FOLLOWERS
                .computeIfAbsent(targetUnit.getId(), key -> new ConcurrentHashMap<>())
                .compute(movingItem.getId(), (key, existing) -> {
                    if (existing != null
                            && existing.roomId == room.getId()
                            && existing.stackItemId == stackItem.getId()) {
                        if (existing.zOverride == null && zOverride != null) {
                            existing.zOverride = zOverride;
                        }
                        existing.ctx = ctx;
                        existing.touch();
                        return existing;
                    }

                    return new UserFollowEntry(
                            room.getId(),
                            stackItem.getId(),
                            movingItem.getId(),
                            zOverride,
                            ctx);
                });
    }

    public static void markUserFollowerProcessed(RoomUnit targetUnit, HabboItem movingItem, long moveStatusTimestamp) {
        if (targetUnit == null || movingItem == null || moveStatusTimestamp <= 0L) {
            return;
        }

        ConcurrentHashMap<Integer, UserFollowEntry> followers = ACTIVE_USER_FOLLOWERS.get(targetUnit.getId());
        if (followers == null) {
            return;
        }

        UserFollowEntry entry = followers.get(movingItem.getId());
        if (entry == null) {
            return;
        }

        entry.markProcessed(moveStatusTimestamp);
    }

    public static boolean isUserFollowerProcessed(RoomUnit targetUnit, HabboItem movingItem, long moveStatusTimestamp) {
        if (targetUnit == null || movingItem == null || moveStatusTimestamp <= 0L) {
            return false;
        }

        ConcurrentHashMap<Integer, UserFollowEntry> followers = ACTIVE_USER_FOLLOWERS.get(targetUnit.getId());
        if (followers == null) {
            return false;
        }

        UserFollowEntry entry = followers.get(movingItem.getId());
        if (entry == null) {
            return false;
        }

        return entry.lastProcessedMoveTimestamp == moveStatusTimestamp;
    }

    public static void processUserFollowers(Room room, Collection<RoomUnit> roomUnits) {
        if (room == null || roomUnits == null || roomUnits.isEmpty()) {
            return;
        }

        for (RoomUnit roomUnit : roomUnits) {
            if (roomUnit == null) {
                continue;
            }

            ConcurrentHashMap<Integer, UserFollowEntry> followers = ACTIVE_USER_FOLLOWERS.get(roomUnit.getId());
            if (followers == null || followers.isEmpty()) {
                continue;
            }

            if (!roomUnit.hasStatus(RoomUnitStatus.MOVE) || roomUnit.getCurrentLocation() == null) {
                ACTIVE_USER_FOLLOWERS.remove(roomUnit.getId(), followers);
                continue;
            }

            long moveStatusTimestamp = roomUnit.getMoveStatusTimestamp();
            List<Integer> toRemove = new ArrayList<>();

            if (shouldSettleFollowersForNewStep(followers, moveStatusTimestamp)) {
                settleUserFollowers(room, followers);
            }

            List<Map.Entry<Integer, UserFollowEntry>> orderedFollowers = new ArrayList<>(followers.entrySet());
            orderedFollowers.sort(Comparator
                    .comparingDouble((Map.Entry<Integer, UserFollowEntry> followerEntry) -> {
                        UserFollowEntry entry = followerEntry.getValue();
                        return (entry != null && entry.zOverride != null) ? entry.zOverride : Double.MAX_VALUE;
                    })
                    .thenComparingInt(Map.Entry::getKey));

            for (Map.Entry<Integer, UserFollowEntry> followerEntry : orderedFollowers) {
                UserFollowEntry entry = followerEntry.getValue();

                if (entry == null || entry.roomId != room.getId() || entry.expiresAt < System.currentTimeMillis()) {
                    toRemove.add(followerEntry.getKey());
                    continue;
                }

                HabboItem stackItem = room.getHabboItem(entry.stackItemId);
                HabboItem movingItem = room.getHabboItem(entry.movingItemId);

                if (stackItem == null || movingItem == null) {
                    toRemove.add(followerEntry.getKey());
                    continue;
                }

                if (moveStatusTimestamp <= 0L || moveStatusTimestamp == entry.lastProcessedMoveTimestamp) {
                    continue;
                }

                int animationElapsed = resolveMoveStepElapsed(roomUnit);
                int animationDuration = resolveMoveStepDuration(roomUnit);
                Double targetZ = resolveFollowerStackZ(room, movingItem, roomUnit.getCurrentLocation(), movingItem.getRotation());
                FurnitureMovementError error = moveFurni(room, stackItem, movingItem, roomUnit.getCurrentLocation(), movingItem.getRotation(), targetZ, null, false, entry.ctx, animationDuration, animationElapsed, WiredMovementsComposer.FURNI_ANCHOR_USER, roomUnit.getId());

                if (error != FurnitureMovementError.NONE && entry.zOverride != null) {
                    error = moveFurni(room, stackItem, movingItem, roomUnit.getCurrentLocation(), movingItem.getRotation(), entry.zOverride, null, false, entry.ctx, animationDuration, animationElapsed, WiredMovementsComposer.FURNI_ANCHOR_USER, roomUnit.getId());
                }

                if (error == FurnitureMovementError.INVALID_MOVE) {
                    toRemove.add(followerEntry.getKey());
                    continue;
                }

                entry.markProcessed(moveStatusTimestamp);
            }

            for (Integer movingItemId : toRemove) {
                followers.remove(movingItemId);
            }

            purgeExpiredFollowers(roomUnit.getId(), followers, true);
        }
    }

    public static boolean hasNoAnimationExtra(Room room, HabboItem stackItem) {
        return getNoAnimationExtra(room, stackItem) != null;
    }

    public static int getAnimationDuration(Room room, HabboItem stackItem, int fallbackDuration) {
        WiredExtraAnimationTime extra = getAnimationTimeExtra(room, stackItem);
        return (extra != null) ? extra.getDurationMs() : fallbackDuration;
    }

    public static WiredMovementPhysics getUserMovementPhysics(Room room, HabboItem stackItem, WiredContext ctx) {
        if (room == null || stackItem == null) {
            return WiredMovementPhysics.NONE;
        }

        return getMovementPhysics(room, stackItem, null, ctx);
    }

    public static int resolveMoveStepElapsed(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return 0;
        }

        long moveStatusTimestamp = roomUnit.getMoveStatusTimestamp();
        if (moveStatusTimestamp <= 0L) {
            return 0;
        }

        return (int) Math.max(0L, Math.min(WiredMovementsComposer.DEFAULT_DURATION, System.currentTimeMillis() - moveStatusTimestamp));
    }

    public static int resolveMoveStepDuration(RoomUnit roomUnit) {
        return WiredMovementsComposer.DEFAULT_DURATION;
    }

    public static Double resolveFollowerStackZ(Room room, HabboItem movingItem, RoomTile targetTile, int rotation) {
        if (room == null || movingItem == null || targetTile == null || room.getLayout() == null) {
            return null;
        }

        double targetZ = room.getStackHeight(targetTile.x, targetTile.y, false, movingItem);
        THashSet<RoomTile> occupiedTiles = room.getLayout().getTilesAt(
                targetTile,
                movingItem.getBaseItem().getWidth(),
                movingItem.getBaseItem().getLength(),
                rotation);

        if (occupiedTiles == null || occupiedTiles.isEmpty()) {
            return targetZ;
        }

        for (RoomTile occupiedTile : occupiedTiles) {
            if (occupiedTile == null) {
                continue;
            }

            targetZ = Math.max(targetZ, room.getStackHeight(occupiedTile.x, occupiedTile.y, false, movingItem));
        }

        return targetZ;
    }

    private static Integer resolveRemainingMoveDuration(RoomUnit roomUnit, HabboItem stackItem, Room room) {
        if (roomUnit == null || stackItem == null || room == null) {
            return null;
        }

        long moveStatusTimestamp = roomUnit.getMoveStatusTimestamp();
        if (moveStatusTimestamp <= 0L) {
            return null;
        }

        int configuredDuration = getAnimationDuration(room, stackItem, WiredMovementsComposer.DEFAULT_DURATION);
        int remainingStepDuration = (int) Math.max(50L, WiredMovementsComposer.DEFAULT_DURATION - Math.max(0L, System.currentTimeMillis() - moveStatusTimestamp));
        return Math.min(configuredDuration, remainingStepDuration);
    }

    private static boolean shouldSettleFollowersForNewStep(ConcurrentHashMap<Integer, UserFollowEntry> followers, long moveStatusTimestamp) {
        if (followers == null || followers.isEmpty() || moveStatusTimestamp <= 0L) {
            return false;
        }

        for (UserFollowEntry entry : followers.values()) {
            if (entry != null && entry.lastProcessedMoveTimestamp > 0L && entry.lastProcessedMoveTimestamp != moveStatusTimestamp) {
                return true;
            }
        }

        return false;
    }

    private static void settleUserFollowers(Room room, ConcurrentHashMap<Integer, UserFollowEntry> followers) {
        if (room == null || followers == null || followers.isEmpty()) {
            return;
        }

        List<Map.Entry<Integer, UserFollowEntry>> entriesToSettle = new ArrayList<>(followers.entrySet());
        entriesToSettle.sort(Comparator
                .comparingDouble((Map.Entry<Integer, UserFollowEntry> followerEntry) -> {
                    UserFollowEntry entry = followerEntry.getValue();
                    return (entry != null && entry.zOverride != null) ? -entry.zOverride : Double.POSITIVE_INFINITY;
                })
                .thenComparingInt(Map.Entry::getKey));

        for (Map.Entry<Integer, UserFollowEntry> followerEntry : entriesToSettle) {
            UserFollowEntry entry = followerEntry.getValue();

            if (entry == null || entry.roomId != room.getId()) {
                continue;
            }

            HabboItem movingItem = room.getHabboItem(entry.movingItemId);
            HabboItem stackItem = room.getHabboItem(entry.stackItemId);
            if (movingItem == null || room.getLayout() == null) {
                continue;
            }

            RoomTile currentTile = room.getLayout().getTile(movingItem.getX(), movingItem.getY());
            if (currentTile == null) {
                continue;
            }

            Double targetZ = (double) room.getLayout().getHeightAtSquare(currentTile.x, currentTile.y);

            if (stackItem != null) {
                FurnitureMovementError error = moveFurni(room, stackItem, movingItem, currentTile, movingItem.getRotation(), targetZ, null, false, entry.ctx, WiredMovementsComposer.DEFAULT_DURATION, 0, WiredMovementsComposer.FURNI_ANCHOR_NONE, 0);

                if (error == FurnitureMovementError.NONE) {
                    continue;
                }
            }

            FurnitureMovementError error = room.moveFurniTo(movingItem, currentTile, movingItem.getRotation(), targetZ, null, true, false);

            if (error != FurnitureMovementError.NONE) {
                room.moveFurniTo(movingItem, currentTile, movingItem.getRotation(), null, true, false);
            }
        }
    }

    private static void purgeExpiredFollowers(int roomUnitId, ConcurrentHashMap<Integer, UserFollowEntry> followers, boolean removeEmpty) {
        if (followers == null) {
            return;
        }

        long now = System.currentTimeMillis();
        followers.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expiresAt < now);

        if (removeEmpty && followers.isEmpty()) {
            ACTIVE_USER_FOLLOWERS.remove(roomUnitId, followers);
        }
    }

    private static boolean hasMovementBehaviorExtra(Room room, HabboItem stackItem) {
        THashSet<InteractionWiredExtra> extras = getMovementExtras(room, stackItem);
        if (extras == null || extras.isEmpty()) {
            return false;
        }

        for (InteractionWiredExtra extra : extras) {
            if (extra instanceof WiredExtraMoveCarryUsers
                    || extra instanceof WiredExtraMoveNoAnimation
                    || extra instanceof WiredExtraAnimationTime
                    || extra instanceof WiredExtraMovePhysics) {
                return true;
            }
        }

        return false;
    }

    private static CarryContext prepareCarryContext(Room room, HabboItem stackItem, HabboItem movingItem, WiredContext ctx) {
        WiredExtraMoveCarryUsers extra = getActiveExtra(room, stackItem);

        if (extra == null || ctx == null || room.getLayout() == null) {
            return CarryContext.disabled();
        }

        RoomTile anchorTile = room.getLayout().getTile(movingItem.getX(), movingItem.getY());
        if (anchorTile == null) {
            return CarryContext.disabled();
        }

        THashSet<RoomTile> occupiedTiles = room.getLayout().getTilesAt(
                anchorTile,
                movingItem.getBaseItem().getWidth(),
                movingItem.getBaseItem().getLength(),
                movingItem.getRotation());

        if (occupiedTiles == null || occupiedTiles.isEmpty()) {
            return CarryContext.disabled();
        }

        Collection<RoomUnit> targetUsers = resolveUsers(room, ctx, extra.getUserSource());
        if (targetUsers == null || targetUsers.isEmpty()) {
            return CarryContext.disabled();
        }

        List<CarriedRoomUnit> carriedUnits = new ArrayList<>();
        HashSet<Integer> carriedIds = new HashSet<>();

        for (RoomUnit roomUnit : targetUsers) {
            if (!isEligibleUser(room, movingItem, roomUnit, occupiedTiles, extra.getCarryMode())) {
                continue;
            }

            CarriedRoomUnit carriedRoomUnit = new CarriedRoomUnit(
                    roomUnit,
                    roomUnit.getCurrentLocation(),
                    roomUnit.getZ(),
                    roomUnit.getZ() - getCarrySurfaceZ(movingItem, roomUnit, roomUnit.getZ()),
                    roomUnit.getX() - anchorTile.x,
                    roomUnit.getY() - anchorTile.y);

            carriedUnits.add(carriedRoomUnit);
            carriedIds.add(roomUnit.getId());
        }

        if (carriedUnits.isEmpty()) {
            return CarryContext.disabled();
        }

        return new CarryContext(true, carriedUnits, carriedIds);
    }

    private static Collection<RoomUnit> resolveUsers(Room room, WiredContext ctx, int userSource) {
        if (userSource == WiredExtraMoveCarryUsers.SOURCE_ALL_ROOM_USERS) {
            return new ArrayList<>(room.getRoomUnits());
        }

        return WiredSourceUtil.resolveUsers(ctx, userSource);
    }

    private static boolean isEligibleUser(Room room, HabboItem movingItem, RoomUnit roomUnit, THashSet<RoomTile> occupiedTiles, int carryMode) {
        if (roomUnit == null
                || roomUnit.getRoomUnitType() != RoomUnitType.USER
                || roomUnit.getCurrentLocation() == null
                || !roomUnit.isInRoom()
                || roomUnit.isWalking()) {
            return false;
        }

        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo != null && habbo.getHabboInfo() != null && habbo.getHabboInfo().getRiding() != null) {
            return false;
        }

        if (!occupiesMovingFurni(occupiedTiles, roomUnit)) {
            return false;
        }

        if (carryMode == WiredExtraMoveCarryUsers.MODE_DIRECTLY_ON_FURNI) {
            return isDirectlyOnMovingFurni(room, movingItem, roomUnit);
        }

        return true;
    }

    private static boolean isDirectlyOnMovingFurni(Room room, HabboItem movingItem, RoomUnit roomUnit) {
        HabboItem topItem = room.getTopItemAt(roomUnit.getX(), roomUnit.getY());
        if (topItem == movingItem) {
            return true;
        }

        double carrySurfaceZ = getCarrySurfaceZ(movingItem, roomUnit, roomUnit.getZ());
        return Math.abs(roomUnit.getZ() - carrySurfaceZ) <= DIRECT_HEIGHT_TOLERANCE;
    }

    private static boolean occupiesMovingFurni(THashSet<RoomTile> occupiedTiles, RoomUnit roomUnit) {
        for (RoomTile occupiedTile : occupiedTiles) {
            if (occupiedTile != null
                    && occupiedTile.x == roomUnit.getX()
                    && occupiedTile.y == roomUnit.getY()) {
                return true;
            }
        }

        return false;
    }

    private static FurnitureMovementError getBlockingUnitError(Room room, HabboItem movingItem, RoomTile targetTile, int rotation, CarryContext carryContext, WiredMovementPhysics movementPhysics) {
        THashSet<RoomTile> occupiedTiles = room.getLayout().getTilesAt(
                targetTile,
                movingItem.getBaseItem().getWidth(),
                movingItem.getBaseItem().getLength(),
                rotation);

        if (occupiedTiles == null || occupiedTiles.isEmpty()) {
            return FurnitureMovementError.NONE;
        }

        for (RoomTile tile : occupiedTiles) {
            for (RoomUnit roomUnit : room.getRoomUnits(tile)) {
                if (roomUnit == null || carryContext.carriedUserIds.contains(roomUnit.getId())) {
                    continue;
                }

                if (movementPhysics.shouldIgnoreUser(roomUnit)) {
                    continue;
                }

                switch (roomUnit.getRoomUnitType()) {
                    case BOT:
                        return FurnitureMovementError.TILE_HAS_BOTS;
                    case PET:
                        return FurnitureMovementError.TILE_HAS_PETS;
                    case USER:
                    default:
                        return FurnitureMovementError.TILE_HAS_HABBOS;
                }
            }
        }

        return FurnitureMovementError.NONE;
    }

    private static void sendAnimatedMove(Room room, HabboItem movingItem, RoomTile oldLocation, double oldZ, RoomTile targetTile, int rotation, CarryContext carryContext, int animationDuration, int animationElapsed, int anchorType, int anchorId) {
        List<CarriedUnitMove> carriedMoves = getCarriedUnitMoves(room, movingItem, targetTile, rotation, carryContext);
        List<WiredMovementsComposer.MovementData> movements = new ArrayList<>();
        movements.add(WiredMovementsComposer.furniMovement(
                movingItem.getId(),
                oldLocation.x,
                oldLocation.y,
                targetTile.x,
                targetTile.y,
                oldZ,
                movingItem.getZ(),
                movingItem.getRotation(),
                animationDuration,
                animationElapsed,
                anchorType,
                anchorId));

        for (CarriedUnitMove carriedMove : carriedMoves) {
            suppressStatusComposer(carriedMove.roomUnit, animationDuration);
            movements.add(WiredMovementsComposer.userSlideMovement(
                    carriedMove.roomUnit.getId(),
                    carriedMove.oldLocation.x,
                    carriedMove.oldLocation.y,
                    carriedMove.destinationTile.x,
                    carriedMove.destinationTile.y,
                    carriedMove.oldZ,
                    carriedMove.newZ,
                    carriedMove.roomUnit.getBodyRotation().getValue(),
                    carriedMove.roomUnit.getHeadRotation().getValue(),
                    animationDuration));
        }

        List<WiredMovementsComposer.MovementData> collectedMovements = COLLECTED_MOVEMENTS.get();

        if (collectedMovements != null) {
            collectedMovements.addAll(movements);
        } else {
            room.sendComposer(new WiredMovementsComposer(movements).compose());
        }

        for (CarriedUnitMove carriedMove : carriedMoves) {
            updateCarriedUnitState(carriedMove);
        }
    }

    private static void applyInstantCarryState(Room room, HabboItem movingItem, RoomTile targetTile, int rotation, CarryContext carryContext) {
        if (!carryContext.active || room == null || movingItem == null || targetTile == null) {
            return;
        }

        List<CarriedUnitMove> carriedMoves = getCarriedUnitMoves(room, movingItem, targetTile, rotation, carryContext);

        for (CarriedUnitMove carriedMove : carriedMoves) {
            updateCarriedUnitStateInstant(carriedMove);

            Habbo habbo = room.getHabbo(carriedMove.roomUnit);
            if (habbo != null && shouldRefreshPostureWithTileUpdate(carriedMove.roomUnit)) {
                THashSet<Habbo> movedHabbos = new THashSet<>();
                movedHabbos.add(habbo);
                room.updateHabbosAt(carriedMove.destinationTile.x, carriedMove.destinationTile.y, movedHabbos);
            }

            room.sendComposer(new RoomUserStatusComposer(carriedMove.roomUnit).compose());
        }
    }

    private static List<CarriedUnitMove> getCarriedUnitMoves(Room room, HabboItem movingItem, RoomTile targetTile, int rotation, CarryContext carryContext) {
        List<CarriedUnitMove> carriedMoves = new ArrayList<>();

        if (!carryContext.active) {
            return carriedMoves;
        }

        THashSet<RoomTile> occupiedTiles = room.getLayout().getTilesAt(
                targetTile,
                movingItem.getBaseItem().getWidth(),
                movingItem.getBaseItem().getLength(),
                rotation);

        for (CarriedRoomUnit carriedRoomUnit : carryContext.carriedUsers) {
            RoomUnit roomUnit = carriedRoomUnit.roomUnit;

            if (roomUnit == null || roomUnit.getCurrentLocation() == null || roomUnit.isWalking()) {
                continue;
            }

            RoomTile destinationTile = room.getLayout().getTile(
                    (short) (targetTile.x + carriedRoomUnit.relativeX),
                    (short) (targetTile.y + carriedRoomUnit.relativeY));

            if (destinationTile == null || destinationTile.state == null || !occupiedTiles.contains(destinationTile)) {
                destinationTile = targetTile;
            }

            double carrySurfaceZ = getCarrySurfaceZ(movingItem, roomUnit, carriedRoomUnit.oldZ);
            double newZ = carrySurfaceZ + carriedRoomUnit.heightOffset;
            carriedMoves.add(new CarriedUnitMove(roomUnit, carriedRoomUnit.oldLocation, carriedRoomUnit.oldZ, destinationTile, newZ));
        }

        return carriedMoves;
    }

    private static boolean shouldRefreshPostureWithTileUpdate(RoomUnit roomUnit) {
        return roomUnit != null
                && (roomUnit.hasStatus(RoomUnitStatus.SIT) || roomUnit.hasStatus(RoomUnitStatus.LAY));
    }

    private static double getCarrySurfaceZ(HabboItem movingItem, RoomUnit roomUnit, double referenceZ) {
        if (movingItem == null) {
            return referenceZ;
        }

        double baseZ = movingItem.getZ();
        double topZ = baseZ + Item.getCurrentHeight(movingItem);

        if (roomUnit != null && (roomUnit.hasStatus(RoomUnitStatus.SIT) || roomUnit.hasStatus(RoomUnitStatus.LAY))) {
            return baseZ;
        }

        if (movingItem.getBaseItem().allowSit() || movingItem.getBaseItem().allowLay()) {
            return (Math.abs(referenceZ - baseZ) <= Math.abs(referenceZ - topZ)) ? baseZ : topZ;
        }

        return topZ;
    }

    private static void updateCarriedUnitState(CarriedUnitMove carriedMove) {
        carriedMove.roomUnit.setLocation(carriedMove.destinationTile);
        carriedMove.roomUnit.setZ(carriedMove.newZ);
        carriedMove.roomUnit.setLastRollerTime(System.currentTimeMillis());
        carriedMove.roomUnit.setPreviousLocation(carriedMove.destinationTile);
        carriedMove.roomUnit.setPreviousLocationZ(carriedMove.newZ);

        if (carriedMove.roomUnit.hasStatus(RoomUnitStatus.SIT)) {
            carriedMove.roomUnit.sitUpdate = true;
        }
    }

    private static void updateCarriedUnitStateInstant(CarriedUnitMove carriedMove) {
        carriedMove.roomUnit.setLocation(carriedMove.destinationTile);
        carriedMove.roomUnit.setZ(carriedMove.newZ);
        carriedMove.roomUnit.setPreviousLocation(carriedMove.destinationTile);
        carriedMove.roomUnit.setPreviousLocationZ(carriedMove.newZ);
        carriedMove.roomUnit.statusUpdate(false);

        if (carriedMove.roomUnit.hasStatus(RoomUnitStatus.SIT)) {
            carriedMove.roomUnit.sitUpdate = true;
        }
    }

    private static void suppressStatusComposer(RoomUnit roomUnit, int duration) {
        if (roomUnit == null) {
            return;
        }

        long suppressedUntil = System.currentTimeMillis() + Math.max(duration, WiredMovementsComposer.DEFAULT_DURATION) + STATUS_SUPPRESSION_GRACE_MS;
        SUPPRESSED_STATUS_COMPOSER_UNTIL.put(roomUnit.getId(), suppressedUntil);
    }

    private static WiredExtraMoveCarryUsers getActiveExtra(Room room, HabboItem stackItem) {
        THashSet<InteractionWiredExtra> extras = getMovementExtras(room, stackItem);
        if (extras == null || extras.isEmpty()) {
            return null;
        }

        for (InteractionWiredExtra extra : extras) {
            if (extra instanceof WiredExtraMoveCarryUsers) {
                return (WiredExtraMoveCarryUsers) extra;
            }
        }

        return null;
    }

    private static WiredExtraMoveNoAnimation getNoAnimationExtra(Room room, HabboItem stackItem) {
        THashSet<InteractionWiredExtra> extras = getMovementExtras(room, stackItem);
        if (extras == null || extras.isEmpty()) {
            return null;
        }

        for (InteractionWiredExtra extra : extras) {
            if (extra instanceof WiredExtraMoveNoAnimation) {
                return (WiredExtraMoveNoAnimation) extra;
            }
        }

        return null;
    }

    private static WiredExtraAnimationTime getAnimationTimeExtra(Room room, HabboItem stackItem) {
        THashSet<InteractionWiredExtra> extras = getMovementExtras(room, stackItem);
        if (extras == null || extras.isEmpty()) {
            return null;
        }

        for (InteractionWiredExtra extra : extras) {
            if (extra instanceof WiredExtraAnimationTime) {
                return (WiredExtraAnimationTime) extra;
            }
        }

        return null;
    }

    private static WiredMovementPhysics getMovementPhysics(Room room, HabboItem stackItem, HabboItem movingItem, WiredContext ctx) {
        WiredExtraMovePhysics extra = getMovementPhysicsExtra(room, stackItem);
        if (extra == null) {
            return WiredMovementPhysics.NONE;
        }

        HashSet<Integer> passThroughFurniIds = new HashSet<>();
        HashSet<Integer> passThroughUserIds = new HashSet<>();
        HashSet<Integer> blockingFurniIds = new HashSet<>();

        if (extra.isMoveThroughFurni()) {
            for (HabboItem item : resolveFurniSources(room, ctx, extra.getMoveThroughFurniSource())) {
                if (item != null && item != movingItem) {
                    passThroughFurniIds.add(item.getId());
                }
            }
        }

        if (extra.isMoveThroughUsers()) {
            for (RoomUnit roomUnit : resolvePhysicsUsers(room, ctx, extra.getMoveThroughUsersSource())) {
                if (roomUnit != null && roomUnit.getRoomUnitType() == RoomUnitType.USER) {
                    passThroughUserIds.add(roomUnit.getId());
                }
            }
        }

        if (extra.isBlockByFurni()) {
            for (HabboItem item : resolveFurniSources(room, ctx, extra.getBlockByFurniSource())) {
                if (item != null && item != movingItem) {
                    blockingFurniIds.add(item.getId());
                }
            }
        }

        return new WiredMovementPhysics(extra.isKeepAltitude(), passThroughFurniIds, passThroughUserIds, blockingFurniIds);
    }

    private static Collection<HabboItem> resolveFurniSources(Room room, WiredContext ctx, int sourceType) {
        if (room == null) {
            return new ArrayList<>();
        }

        if (sourceType == WiredExtraMovePhysics.SOURCE_ALL_ROOM) {
            return new ArrayList<>(room.getFloorItems());
        }

        if (ctx == null) {
            return new ArrayList<>();
        }

        return WiredSourceUtil.resolveItemsRaw(ctx, sourceType, null);
    }

    private static Collection<RoomUnit> resolvePhysicsUsers(Room room, WiredContext ctx, int userSource) {
        if (room == null) {
            return new ArrayList<>();
        }

        if (userSource == WiredExtraMovePhysics.SOURCE_ALL_ROOM) {
            return new ArrayList<>(room.getRoomUnits());
        }

        if (ctx == null) {
            return new ArrayList<>();
        }

        return WiredSourceUtil.resolveUsersRaw(ctx, userSource);
    }

    private static WiredExtraMovePhysics getMovementPhysicsExtra(Room room, HabboItem stackItem) {
        THashSet<InteractionWiredExtra> extras = getMovementExtras(room, stackItem);
        if (extras == null || extras.isEmpty()) {
            return null;
        }

        for (InteractionWiredExtra extra : extras) {
            if (extra instanceof WiredExtraMovePhysics) {
                return (WiredExtraMovePhysics) extra;
            }
        }

        return null;
    }

    private static THashSet<InteractionWiredExtra> getMovementExtras(Room room, HabboItem stackItem) {
        if (room == null || stackItem == null || room.getRoomSpecialTypes() == null) {
            return null;
        }

        THashSet<InteractionWiredExtra> extras = room.getRoomSpecialTypes().getExtras(stackItem.getX(), stackItem.getY());
        if (extras == null || extras.isEmpty()) {
            return null;
        }

        return extras;
    }

    private static final class CarryContext {
        private final boolean active;
        private final List<CarriedRoomUnit> carriedUsers;
        private final HashSet<Integer> carriedUserIds;

        private CarryContext(boolean active, List<CarriedRoomUnit> carriedUsers, HashSet<Integer> carriedUserIds) {
            this.active = active;
            this.carriedUsers = carriedUsers;
            this.carriedUserIds = carriedUserIds;
        }

        private static CarryContext disabled() {
            return new CarryContext(false, new ArrayList<>(), new HashSet<>());
        }
    }

    private static final class CarriedRoomUnit {
        private final RoomUnit roomUnit;
        private final RoomTile oldLocation;
        private final double oldZ;
        private final double heightOffset;
        private final int relativeX;
        private final int relativeY;

        private CarriedRoomUnit(RoomUnit roomUnit, RoomTile oldLocation, double oldZ, double heightOffset, int relativeX, int relativeY) {
            this.roomUnit = roomUnit;
            this.oldLocation = oldLocation;
            this.oldZ = oldZ;
            this.heightOffset = heightOffset;
            this.relativeX = relativeX;
            this.relativeY = relativeY;
        }
    }

    private static final class CarriedUnitMove {
        private final RoomUnit roomUnit;
        private final RoomTile oldLocation;
        private final double oldZ;
        private final RoomTile destinationTile;
        private final double newZ;

        private CarriedUnitMove(RoomUnit roomUnit, RoomTile oldLocation, double oldZ, RoomTile destinationTile, double newZ) {
            this.roomUnit = roomUnit;
            this.oldLocation = oldLocation;
            this.oldZ = oldZ;
            this.destinationTile = destinationTile;
            this.newZ = newZ;
        }
    }

    private static final class UserFollowEntry {
        private final int roomId;
        private final int stackItemId;
        private final int movingItemId;
        private Double zOverride;
        private WiredContext ctx;
        private long expiresAt;
        private long lastProcessedMoveTimestamp;

        private UserFollowEntry(int roomId, int stackItemId, int movingItemId, Double zOverride, WiredContext ctx) {
            this.roomId = roomId;
            this.stackItemId = stackItemId;
            this.movingItemId = movingItemId;
            this.zOverride = zOverride;
            this.ctx = ctx;
            this.touch();
        }

        private void markProcessed(long moveStatusTimestamp) {
            this.lastProcessedMoveTimestamp = moveStatusTimestamp;
            this.touch();
        }

        private void touch() {
            this.expiresAt = System.currentTimeMillis() + USER_FOLLOWER_TTL_MS;
        }
    }

    public static void cleanupRoomUnit(RoomUnit roomUnit) {
        if (roomUnit != null) {
            SUPPRESSED_STATUS_COMPOSER_UNTIL.remove(roomUnit.getId());
            ACTIVE_USER_FOLLOWERS.remove(roomUnit.getId());
        }
    }
}
