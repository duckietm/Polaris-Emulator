package com.eu.habbo.threading.runnables.teleport;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleport;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleportTile;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredFreezeUtil;

class TeleportActionThree implements Runnable {
    private final HabboItem currentTeleport;
    private final Room room;
    private final GameClient client;

    public TeleportActionThree(HabboItem currentTeleport, Room room, GameClient client) {
        this.currentTeleport = currentTeleport;
        this.client = client;
        this.room = room;
    }

    @Override
    public void run() {
        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != this.room) {
            this.client.getHabbo().getHabboInfo().setLoadingRoom(0);
            this.client.getHabbo().getRoomUnit().isTeleporting = false;
            WiredFreezeUtil.restoreWalkState(this.client.getHabbo().getRoomUnit());
            return;
        }

        HabboItem targetTeleport;
        Room targetRoom = this.room;

        if (this.currentTeleport.getRoomId() != ((InteractionTeleport) this.currentTeleport).getTargetRoomId()) {
            targetRoom = Emulator.getGameEnvironment()
                    .getRoomManager()
                    .loadRoom(((InteractionTeleport) this.currentTeleport).getTargetRoomId());
        }

        if (targetRoom == null) {
            Emulator.getThreading().run(new TeleportActionFive(this.currentTeleport, this.room, this.client), 0);
            return;
        }

        if (targetRoom.isPreLoaded()) {
            targetRoom.loadData();
        }

        targetTeleport = targetRoom.getHabboItem(((InteractionTeleport) this.currentTeleport).getTargetId());

        if (targetTeleport == null) {
            Emulator.getThreading().run(new TeleportActionFive(this.currentTeleport, this.room, this.client), 0);
            return;
        }

        RoomTile teleportLocation = targetRoom.getLayout().getTile(targetTeleport.getX(), targetTeleport.getY());

        if (teleportLocation == null) {
            Emulator.getThreading().run(new TeleportActionFive(this.currentTeleport, this.room, this.client), 0);
            return;
        }
        this.client.getHabbo().getRoomUnit().setLocation(teleportLocation);
        this.client.getHabbo().getRoomUnit().getPath().clear();
        this.client.getHabbo().getRoomUnit().removeStatus(RoomUnitStatus.MOVE);
        this.client.getHabbo().getRoomUnit().setZ(teleportLocation.getStackHeight());
        this.client.getHabbo().getRoomUnit().setPreviousLocationZ(teleportLocation.getStackHeight());

        if (targetRoom != this.room) {
            this.room.removeHabbo(this.client.getHabbo(), false);
            Emulator.getGameEnvironment()
                    .getRoomManager()
                    .enterRoom(
                            this.client.getHabbo(),
                            targetRoom.getId(),
                            "",
                            Emulator.getConfig().getBoolean("hotel.teleport.locked.allowed"),
                            teleportLocation);
        }

        this.client.getHabbo().getRoomUnit().setRotation(RoomUserRotation.values()[targetTeleport.getRotation() % 8]);
        this.client.getHabbo().getRoomUnit().statusUpdate(true);

        targetTeleport.setExtradata("2");
        targetRoom.updateItem(targetTeleport);
        // targetRoom.updateHabbo(this.client.getHabbo());
        // LOGGER.info((targetTeleport.getX() + " | " + targetTeleport.getY());
        this.client.getHabbo().getHabboInfo().setCurrentRoom(targetRoom);
        com.eu.habbo.habbohotel.quests.QuestProgressEvents.progress(
                this.client.getHabbo(), com.eu.habbo.habbohotel.quests.QuestGoalType.USE_TELEPORT, 1);
        // Emulator.getThreading().run(new HabboItemNewState(this.currentTeleport, this.room, "0"), 500);
        Emulator.getThreading()
                .run(
                        new TeleportActionFour(targetTeleport, targetRoom, this.client),
                        this.currentTeleport instanceof InteractionTeleportTile ? 0 : 500);
    }
}
