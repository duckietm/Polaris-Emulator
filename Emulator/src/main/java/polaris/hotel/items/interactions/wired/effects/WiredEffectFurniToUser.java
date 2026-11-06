package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.FurnitureMovementError;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredFairDeal;
import polaris.hotel.wired.core.WiredMoveCarryHelper;
import polaris.messages.outgoing.rooms.WiredMovementsComposer;

public class WiredEffectFurniToUser extends WiredEffectUserFurniBase {
    public static final WiredEffectType type = WiredEffectType.FURNI_TO_USER;

    public WiredEffectFurniToUser(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectFurniToUser(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null) {
            return;
        }

        List<UserItem> items = new ArrayList<>(this.resolveItems(ctx));
        items.removeIf(item -> item == null);
        List<User> users = this.resolveUsers(room, ctx);
        users.removeIf(user -> user.getRoomUnit() == null);

        if (items.isEmpty() || users.isEmpty()) {
            return;
        }

        // AIR deals the furni evenly over the users (one each with as many furni as users, the extra ones to
        // different users each firing) and moves them in a random order.
        Collections.shuffle(items, ThreadLocalRandom.current());
        List<User> owners = WiredFairDeal.deal(users, items.size());
        Map<User, List<UserItem>> dealt = new LinkedHashMap<>();

        for (int i = 0; i < items.size(); i++) {
            dealt.computeIfAbsent(owners.get(i), user -> new ArrayList<>()).add(items.get(i));
        }

        dealt.forEach((user, dealtItems) -> this.moveToUser(room, ctx, user, dealtItems));
    }

    private void moveToUser(Room room, WiredContext ctx, User user, List<UserItem> items) {
        items.sort(Comparator.comparingDouble(UserItem::getZ).thenComparingInt(UserItem::getId));

        Map<Integer, Double> followerZOverrides = new HashMap<>();

        for (UserItem item : items) {
            followerZOverrides.put(item.getId(), item.getZ());
        }

        RoomUnit roomUnit = user.getRoomUnit();
        boolean hasActiveMoveStatus = roomUnit.hasStatus(RoomUnitStatus.MOVE);
        long moveStatusTimestamp = hasActiveMoveStatus ? roomUnit.getMoveStatusTimestamp() : 0L;

        if (roomUnit.isWalking()) {
            for (UserItem item : items) {
                if (item == null) {
                    continue;
                }

                WiredMoveCarryHelper.registerUserFollower(
                        room, this, item, roomUnit, followerZOverrides.get(item.getId()), ctx);
            }

            if (!hasActiveMoveStatus) {
                return;
            }
        }

        RoomTile targetTile = this.resolveTargetTile(user);
        if (targetTile == null) {
            return;
        }

        Integer animationDurationOverride = WiredMoveCarryHelper.hasNoAnimationExtra(room, this)
                ? null
                : this.resolveFollowAnimationDuration(room, user, this);
        int anchorType = hasActiveMoveStatus
                ? WiredMovementsComposer.FURNI_ANCHOR_USER
                : WiredMovementsComposer.FURNI_ANCHOR_NONE;
        int anchorId = hasActiveMoveStatus ? roomUnit.getId() : 0;

        if (hasActiveMoveStatus) {
            int animationDuration = WiredMoveCarryHelper.resolveMoveStepDuration(roomUnit);
            int animationElapsed = WiredMoveCarryHelper.resolveMoveStepElapsed(roomUnit);

            for (UserItem item : items) {
                if (item == null || WiredMoveCarryHelper.isUserFollowerProcessed(roomUnit, item, moveStatusTimestamp)) {
                    continue;
                }

                Double targetZ = WiredMoveCarryHelper.resolveFollowerStackZ(room, item, targetTile, item.getRotation());
                FurnitureMovementError error = WiredMoveCarryHelper.moveFurni(
                        room,
                        this,
                        item,
                        targetTile,
                        item.getRotation(),
                        targetZ,
                        null,
                        false,
                        ctx,
                        animationDuration,
                        animationElapsed,
                        WiredMovementsComposer.FURNI_ANCHOR_USER,
                        roomUnit.getId());
                if (error != FurnitureMovementError.NONE) {
                    Double fallbackZ = followerZOverrides.get(item.getId());

                    if (fallbackZ != null) {
                        error = WiredMoveCarryHelper.moveFurni(
                                room,
                                this,
                                item,
                                targetTile,
                                item.getRotation(),
                                fallbackZ,
                                null,
                                false,
                                ctx,
                                animationDuration,
                                animationElapsed,
                                WiredMovementsComposer.FURNI_ANCHOR_USER,
                                roomUnit.getId());
                    }
                }

                if (error == FurnitureMovementError.NONE) {
                    WiredMoveCarryHelper.markUserFollowerProcessed(roomUnit, item, moveStatusTimestamp);
                }
            }
        }

        for (UserItem item : items) {
            if (item == null) {
                continue;
            }

            if (hasActiveMoveStatus
                    && WiredMoveCarryHelper.isUserFollowerProcessed(roomUnit, item, moveStatusTimestamp)) {
                continue;
            }

            Double targetZ = WiredMoveCarryHelper.resolveFollowerStackZ(room, item, targetTile, item.getRotation());
            FurnitureMovementError error = WiredMoveCarryHelper.moveFurni(
                    room,
                    this,
                    item,
                    targetTile,
                    item.getRotation(),
                    targetZ,
                    null,
                    false,
                    ctx,
                    animationDurationOverride,
                    null,
                    anchorType,
                    anchorId);
            if (error == FurnitureMovementError.NONE) {
                continue;
            }

            Double fallbackZ = followerZOverrides.get(item.getId());

            if (fallbackZ != null) {
                WiredMoveCarryHelper.moveFurni(
                        room,
                        this,
                        item,
                        targetTile,
                        item.getRotation(),
                        fallbackZ,
                        null,
                        false,
                        ctx,
                        animationDurationOverride,
                        null,
                        anchorType,
                        anchorId);
            }
        }
    }

    @Deprecated
    @Override
    public boolean execute(polaris.hotel.rooms.RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }
}
