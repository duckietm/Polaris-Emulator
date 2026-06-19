package com.eu.habbo.threading.runnables.teleport;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleportTile;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredFreezeUtil;
import com.eu.habbo.threading.runnables.HabboItemNewState;
import com.eu.habbo.threading.runnables.RoomUnitWalkToLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

class TeleportActionFive implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TeleportActionFive.class);

    private final HabboItem currentTeleport;
    private final Room room;
    private final GameClient client;

    public TeleportActionFive(HabboItem currentTeleport, Room room, GameClient client) {
        this.currentTeleport = currentTeleport;
        this.client = client;
        this.room = room;
    }

    @Override
    public void run() {
        RoomUnit unit = this.client.getHabbo().getRoomUnit();

        unit.isLeavingTeleporter = false;
        unit.isTeleporting = false;
        unit.setCanWalk(true);

        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != this.room) {
            this.client.getHabbo().getHabboInfo().setLoadingRoom(0);
            return;
        }

        //if (!(this.currentTeleport instanceof InteractionTeleportTile))

        if (this.room.getLayout() == null || this.currentTeleport == null) return;

        RoomTile currentLocation = this.room.getLayout().getTile(this.currentTeleport.getX(), this.currentTeleport.getY());
        RoomTile tile = this.room.getLayout().getTileInFront(currentLocation, this.currentTeleport.getRotation());

        if (tile != null) {
            List<Runnable> onSuccess = new ArrayList<Runnable>();
            onSuccess.add(() -> {
                unit.setCanLeaveRoomByDoor(true);
                WiredFreezeUtil.restoreWalkState(unit);

                Emulator.getThreading().run(() -> {
                    unit.isLeavingTeleporter = false;
                }, 300);
            });

            unit.setCanLeaveRoomByDoor(false);
            unit.setGoalLocation(tile);
            unit.statusUpdate(true);
            unit.isLeavingTeleporter = true;
            Emulator.getThreading().run(new RoomUnitWalkToLocation(unit, tile, room, onSuccess, onSuccess));
        } else {
            WiredFreezeUtil.restoreWalkState(unit);
        }

        this.currentTeleport.setExtradata("1");
        this.room.updateItem(this.currentTeleport);

        Emulator.getThreading().run(new HabboItemNewState(this.currentTeleport, this.room, "0"), 1000);

        HabboItem teleportTile = this.room.getTopItemAt(unit.getX(), unit.getY());

        if (teleportTile != null && teleportTile instanceof InteractionTeleportTile && teleportTile != this.currentTeleport) {
            try {
                teleportTile.onWalkOn(unit, this.room, new Object[]{});
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }
    }
}
