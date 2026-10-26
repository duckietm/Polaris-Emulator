package com.eu.habbo.threading.runnables.teleport;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredFreezeUtil;

class TeleportActionFour implements Runnable {
    private final HabboItem currentTeleport;
    private final Room room;
    private final Habbo habbo;

    public TeleportActionFour(HabboItem currentTeleport, Room room, GameClient client) {
        this(currentTeleport, room, client == null ? null : client.getHabbo());
    }

    public TeleportActionFour(HabboItem currentTeleport, Room room, Habbo habbo) {
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
            this.currentTeleport.setExtradata("0");
            this.room.updateItem(this.currentTeleport);
            return;
        }

        if (this.habbo.getRoomUnit() != null) {
            this.habbo.getRoomUnit().isLeavingTeleporter = true;
        }

        Emulator.getThreading().run(new TeleportActionFive(this.currentTeleport, this.room, this.habbo), 500);
    }
}
