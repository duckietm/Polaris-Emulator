package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        List<HabboItem> items = new ArrayList<>(this.resolveItems(ctx));
        Habbo habbo = this.resolveLastHabbo(room, ctx);

        if (room == null || habbo == null || habbo.getRoomUnit() == null) {
            return;
        }

        items.removeIf(item -> item == null);

        if (items.isEmpty()) {
            return;
        }

        items.sort(Comparator
                .comparingDouble(HabboItem::getZ)
                .thenComparingInt(HabboItem::getId));

        Map<Integer, Double> followerZOverrides = new HashMap<>();

        for (HabboItem item : items) {
            followerZOverrides.put(item.getId(), item.getZ());
        }

        RoomUnit roomUnit = habbo.getRoomUnit();
        boolean hasActiveMoveStatus = roomUnit.hasStatus(RoomUnitStatus.MOVE);
        long moveStatusTimestamp = hasActiveMoveStatus ? roomUnit.getMoveStatusTimestamp() : 0L;

        if (roomUnit.isWalking()) {
            for (HabboItem item : items) {
                if (item == null) {
                    continue;
                }

                WiredMoveCarryHelper.registerUserFollower(room, this, item, roomUnit, followerZOverrides.get(item.getId()), ctx);
            }

            if (!hasActiveMoveStatus) {
                return;
            }
        }

        RoomTile targetTile = this.resolveTargetTile(habbo);
        if (targetTile == null) {
            return;
        }

        Integer animationDurationOverride = WiredMoveCarryHelper.hasNoAnimationExtra(room, this)
                ? null
                : this.resolveFollowAnimationDuration(room, habbo, this);
        int anchorType = hasActiveMoveStatus ? WiredMovementsComposer.FURNI_ANCHOR_USER : WiredMovementsComposer.FURNI_ANCHOR_NONE;
        int anchorId = hasActiveMoveStatus ? roomUnit.getId() : 0;

        if (hasActiveMoveStatus) {
            int animationDuration = WiredMoveCarryHelper.resolveMoveStepDuration(roomUnit);
            int animationElapsed = WiredMoveCarryHelper.resolveMoveStepElapsed(roomUnit);

            for (HabboItem item : items) {
                if (item == null || WiredMoveCarryHelper.isUserFollowerProcessed(roomUnit, item, moveStatusTimestamp)) {
                    continue;
                }

                Double targetZ = WiredMoveCarryHelper.resolveFollowerStackZ(room, item, targetTile, item.getRotation());
                FurnitureMovementError error = WiredMoveCarryHelper.moveFurni(room, this, item, targetTile, item.getRotation(), targetZ, null, false, ctx, animationDuration, animationElapsed, WiredMovementsComposer.FURNI_ANCHOR_USER, roomUnit.getId());
                if (error != FurnitureMovementError.NONE) {
                    Double fallbackZ = followerZOverrides.get(item.getId());

                    if (fallbackZ != null) {
                        error = WiredMoveCarryHelper.moveFurni(room, this, item, targetTile, item.getRotation(), fallbackZ, null, false, ctx, animationDuration, animationElapsed, WiredMovementsComposer.FURNI_ANCHOR_USER, roomUnit.getId());
                    }
                }

                if (error == FurnitureMovementError.NONE) {
                    WiredMoveCarryHelper.markUserFollowerProcessed(roomUnit, item, moveStatusTimestamp);
                }
            }
        }

        for (HabboItem item : items) {
            if (item == null) {
                continue;
            }

            if (hasActiveMoveStatus && WiredMoveCarryHelper.isUserFollowerProcessed(roomUnit, item, moveStatusTimestamp)) {
                continue;
            }

            Double targetZ = WiredMoveCarryHelper.resolveFollowerStackZ(room, item, targetTile, item.getRotation());
            FurnitureMovementError error = WiredMoveCarryHelper.moveFurni(room, this, item, targetTile, item.getRotation(), targetZ, null, false, ctx, animationDurationOverride, null, anchorType, anchorId);
            if (error == FurnitureMovementError.NONE) {
                continue;
            }

            Double fallbackZ = followerZOverrides.get(item.getId());

            if (fallbackZ != null) {
                WiredMoveCarryHelper.moveFurni(room, this, item, targetTile, item.getRotation(), fallbackZ, null, false, ctx, animationDurationOverride, null, anchorType, anchorId);
            }
        }
    }

    @Deprecated
    @Override
    public boolean execute(com.eu.habbo.habbohotel.rooms.RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

}
