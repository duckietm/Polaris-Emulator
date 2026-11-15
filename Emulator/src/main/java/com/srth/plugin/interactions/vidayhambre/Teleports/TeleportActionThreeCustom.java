package com.srth.plugin.interactions.vidayhambre.Teleports;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleportTile;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.HabboItem;

class TeleportActionThreeCustom implements Runnable {
    private final HabboItem currentTeleport;
    private final Room room;
    private final GameClient client;

    public TeleportActionThreeCustom(HabboItem currentTeleport, Room room, GameClient client) {
        this.currentTeleport = currentTeleport;
        this.client = client;
        this.room = room;
    }

    @Override
    public void run() {
        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != this.room)
            return;

        HabboItem targetTeleport;
        Room targetRoom = this.room;

        if (this.currentTeleport.getRoomId() != ((InteractionTeleports) this.currentTeleport).getTargetRoomId()) {
            targetRoom = Emulator.getGameEnvironment().getRoomManager()
                    .loadRoom(((InteractionTeleports) this.currentTeleport).getTargetRoomId());
        }

        if (targetRoom == null) {
            Emulator.getThreading().run(new TeleportActionFiveCustom(this.currentTeleport, this.room, this.client), 0);
            return;
        }

        if (targetRoom.isPreLoaded()) {
            targetRoom.loadData();
        }

        targetTeleport = targetRoom.getHabboItem(((InteractionTeleports) this.currentTeleport).getTargetId());

        if (targetTeleport == null) {
            Emulator.getThreading().run(new TeleportActionFiveCustom(this.currentTeleport, this.room, this.client), 0);
            return;
        }

        RoomTile teleportLocation = targetRoom.getLayout().getTile(targetTeleport.getX(), targetTeleport.getY());

        if (teleportLocation == null) {
            Emulator.getThreading().run(new TeleportActionFiveCustom(this.currentTeleport, this.room, this.client), 0);
            return;
        }
        this.client.getHabbo().getRoomUnit().setLocation(teleportLocation);
        this.client.getHabbo().getRoomUnit().getPath().clear();
        this.client.getHabbo().getRoomUnit().removeStatus(RoomUnitStatus.MOVE);
        this.client.getHabbo().getRoomUnit().setZ(teleportLocation.getStackHeight());
        this.client.getHabbo().getRoomUnit().setPreviousLocationZ(teleportLocation.getStackHeight());

        if (targetRoom != this.room) {
            this.room.removeHabbo(this.client.getHabbo(), false);
            Emulator.getGameEnvironment().getRoomManager().enterRoom(this.client.getHabbo(), targetRoom.getId(), "",
                    Emulator.getConfig().getBoolean("hotel.teleport.locked.allowed"), teleportLocation);
        }

        this.client.getHabbo().getRoomUnit().setRotation(RoomUserRotation.values()[targetTeleport.getRotation() % 8]);
        this.client.getHabbo().getRoomUnit().statusUpdate(true);

        targetTeleport.setExtradata("2");
        targetRoom.updateItem(targetTeleport);
        // targetRoom.updateHabbo(this.client.getHabbo());
        // LOGGER.info((targetTeleport.getX() + " | " + targetTeleport.getY());
        this.client.getHabbo().getHabboInfo().setCurrentRoom(targetRoom);
        // Emulator.getThreading().run(new HabboItemNewState(this.currentTeleport,
        // this.room, "0"), 500);
        Emulator.getThreading().run(new TeleportActionFourCustom(targetTeleport, targetRoom, this.client),
                this.currentTeleport instanceof InteractionTeleportTile ? 0 : 200);

    }
}
