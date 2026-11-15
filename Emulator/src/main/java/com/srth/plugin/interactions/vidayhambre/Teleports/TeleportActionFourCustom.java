package com.srth.plugin.interactions.vidayhambre.Teleports;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;

class TeleportActionFourCustom implements Runnable {
    private final HabboItem currentTeleport;
    private final Room room;
    private final GameClient client;

    public TeleportActionFourCustom(HabboItem currentTeleport, Room room, GameClient client) {
        this.currentTeleport = currentTeleport;
        this.client = client;
        this.room = room;
    }

    @Override
    public void run() {
        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != this.room) {
            this.client.getHabbo().getRoomUnit().setCanWalk(true);
            this.currentTeleport.setExtradata("0");
            this.room.updateItem(this.currentTeleport);
            return;
        }

        if (this.client.getHabbo().getRoomUnit() != null) {
            this.client.getHabbo().getRoomUnit().isLeavingTeleporter = true;
        }

        Emulator.getThreading().run(new TeleportActionFiveCustom(this.currentTeleport, this.room, this.client), 200);
    }
}
