package com.eu.habbo.threading.runnables.teleport;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleportTile;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredFreezeUtil;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;

public class TeleportActionOne implements Runnable {
    private final HabboItem currentTeleport;
    private final Room room;
    private final Habbo habbo;

    public TeleportActionOne(HabboItem currentTeleport, Room room, GameClient client) {
        this(currentTeleport, room, client == null ? null : client.getHabbo());
    }

    public TeleportActionOne(HabboItem currentTeleport, Room room, Habbo habbo) {
        this.currentTeleport = currentTeleport;
        this.habbo = habbo;
        this.room = room;
    }

    @Override
    public void run() {
        TeleportChain.run(this.habbo, this.currentTeleport, this.room, this::step);
    }

    private void step() {
        if (this.habbo.getHabboInfo().getCurrentRoom() != this.room) {
            this.habbo.getHabboInfo().setLoadingRoom(0);
            this.habbo.getRoomUnit().isTeleporting = false;
            WiredFreezeUtil.restoreWalkState(this.habbo.getRoomUnit());
            return;
        }

        int delay = 500;

        if (this.currentTeleport instanceof InteractionTeleportTile) {
            delay = 0;
        }

        if (this.habbo.getRoomUnit().getCurrentLocation()
                != this.room.getLayout().getTile(this.currentTeleport.getX(), this.currentTeleport.getY())) {
            this.habbo
                    .getRoomUnit()
                    .setLocation(
                            this.room.getLayout().getTile(this.currentTeleport.getX(), this.currentTeleport.getY()));
            this.habbo
                    .getRoomUnit()
                    .setRotation(RoomUserRotation.values()[(this.currentTeleport.getRotation() + 4) % 8]);
            this.habbo
                    .getRoomUnit()
                    .setStatus(
                            RoomUnitStatus.MOVE,
                            this.currentTeleport.getX() + "," + this.currentTeleport.getY() + ","
                                    + this.currentTeleport.getZ());
            this.room.scheduledComposers.add(new RoomUserStatusComposer(this.habbo.getRoomUnit()).compose());
            this.habbo
                    .getRoomUnit()
                    .setLocation(
                            this.room.getLayout().getTile(this.currentTeleport.getX(), this.currentTeleport.getY()));
        }

        Emulator.getThreading().run(new TeleportActionTwo(this.currentTeleport, this.room, this.habbo), delay);
    }
}
