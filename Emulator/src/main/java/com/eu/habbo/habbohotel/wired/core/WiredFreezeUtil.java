package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;

public final class WiredFreezeUtil {
    private static final String CACHE_ACTIVE = "wired.freeze.active";
    private static final String CACHE_EFFECT_ID = "wired.freeze.effect_id";
    private static final String CACHE_CANCEL_ON_TELEPORT = "wired.freeze.cancel_on_teleport";

    private WiredFreezeUtil() {
    }

    public static boolean isFrozen(RoomUnit roomUnit) {
        return roomUnit != null && Boolean.TRUE.equals(roomUnit.getCacheable().get(CACHE_ACTIVE));
    }

    public static void freeze(Room room, RoomUnit roomUnit, int effectId, boolean cancelOnTeleport) {
        if (room == null || roomUnit == null || effectId <= 0) {
            return;
        }

        roomUnit.getCacheable().put(CACHE_ACTIVE, true);
        roomUnit.getCacheable().put(CACHE_EFFECT_ID, effectId);
        roomUnit.getCacheable().put(CACHE_CANCEL_ON_TELEPORT, cancelOnTeleport);

        roomUnit.stopWalking();
        roomUnit.setCanWalk(false);
        roomUnit.statusUpdate(true);

        room.giveEffect(roomUnit, effectId, Integer.MAX_VALUE);
        room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
    }

    public static void unfreeze(Room room, RoomUnit roomUnit) {
        if (roomUnit == null) {
            return;
        }

        roomUnit.getCacheable().remove(CACHE_ACTIVE);
        roomUnit.getCacheable().remove(CACHE_EFFECT_ID);
        roomUnit.getCacheable().remove(CACHE_CANCEL_ON_TELEPORT);

        roomUnit.stopWalking();
        roomUnit.setCanWalk(true);
        roomUnit.statusUpdate(true);

        if (room != null) {
            room.giveEffect(roomUnit, 0, -1);
            room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
        } else {
            roomUnit.setEffectId(0, 0);
        }
    }

    public static void onTeleport(Room room, RoomUnit roomUnit) {
        if (!isFrozen(roomUnit)) {
            return;
        }

        if (Boolean.TRUE.equals(roomUnit.getCacheable().get(CACHE_CANCEL_ON_TELEPORT))) {
            unfreeze(room, roomUnit);
        }
    }

    public static void restoreWalkState(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return;
        }

        roomUnit.setCanWalk(!isFrozen(roomUnit));
    }
}
