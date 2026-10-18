package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.wired.WiredGame;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;

public final class WiredFreezeUtil {
    private static final String CACHE_ACTIVE = "wired.freeze.active";
    private static final String CACHE_EFFECT_ID = "wired.freeze.effect_id";
    private static final String CACHE_CANCEL_ON_TELEPORT = "wired.freeze.cancel_on_teleport";

    private WiredFreezeUtil() {}

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

        // A standing user only gets the freeze effect, as in Habbo; a walking one also gets the status that shows
        // the stop. Behaviour observed in plus-emulator/PlusEMU d948347a, reimplemented.
        boolean wasWalking = roomUnit.isWalking() || roomUnit.hasStatus(RoomUnitStatus.MOVE);
        roomUnit.stopWalking();
        roomUnit.setCanWalk(false);

        room.giveEffect(roomUnit, effectId, Integer.MAX_VALUE);
        if (wasWalking) {
            roomUnit.statusUpdate(true);
            room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
        }
    }

    public static void unfreeze(Room room, RoomUnit roomUnit) {
        unfreeze(room, roomUnit, true);
    }

    private static void unfreeze(Room room, RoomUnit roomUnit, boolean sendEffect) {
        if (roomUnit == null) {
            return;
        }

        Object freezeEffect = roomUnit.getCacheable().get(CACHE_EFFECT_ID);

        roomUnit.getCacheable().remove(CACHE_ACTIVE);
        roomUnit.getCacheable().remove(CACHE_EFFECT_ID);
        roomUnit.getCacheable().remove(CACHE_CANCEL_ON_TELEPORT);

        roomUnit.stopWalking();
        roomUnit.setCanWalk(true);

        // Only the freeze's own effect comes off: an effect put on after the freeze (a wired give
        // effect, an item, a costume) stays. Without a stored freeze effect, the old behaviour.
        boolean clearsEffect = !(freezeEffect instanceof Integer freezeEffectId)
                || freezeEffectId <= 0
                || roomUnit.getEffectId() == freezeEffectId;

        // Unfreezing sends only the restored effect, no user update; a Wired team member gets the team effect back
        // (223-226). Behaviour observed in plus-emulator/PlusEMU 60bb18e8, reimplemented.
        if (!clearsEffect) {
            return;
        }

        if (room == null) {
            roomUnit.setEffectId(0, 0);
        } else if (sendEffect) {
            room.giveEffect(roomUnit, restoredEffect(room, roomUnit), -1);
        } else {
            // The teleport's own effect is showing; its end shows the restored effect.
            int restored = restoredEffect(room, roomUnit);
            roomUnit.setEffectId(restored, restored > 0 ? Integer.MAX_VALUE : 0);
        }
    }

    /** The effect an unfrozen user goes back to: their Wired team's effect, or none. */
    static int restoredEffect(Room room, RoomUnit roomUnit) {
        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo == null || habbo.getHabboInfo() == null) return 0;
        if (habbo.getHabboInfo().getCurrentGame() != WiredGame.class) return 0;
        GamePlayer player = habbo.getHabboInfo().getGamePlayer();
        if (player == null || player.getTeamColor() == null) return 0;
        return WiredGame.teamEffectId(player.getTeamColor());
    }

    public static void onTeleport(Room room, RoomUnit roomUnit) {
        if (!isFrozen(roomUnit)) {
            return;
        }

        if (Boolean.TRUE.equals(roomUnit.getCacheable().get(CACHE_CANCEL_ON_TELEPORT))) {
            unfreeze(room, roomUnit);
        }
    }

    /**
     * A wired teleport landing: a freeze saved with "cancel on teleport" ends without an effect
     * packet of its own, so only the teleport effect shows, as in Habbo.
     */
    public static void onWiredTeleport(Room room, RoomUnit roomUnit) {
        if (!isFrozen(roomUnit)) {
            return;
        }

        if (Boolean.TRUE.equals(roomUnit.getCacheable().get(CACHE_CANCEL_ON_TELEPORT))) {
            unfreeze(room, roomUnit, false);
        }
    }

    public static void restoreWalkState(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return;
        }

        roomUnit.setCanWalk(!isFrozen(roomUnit));
    }
}
