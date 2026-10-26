package com.eu.habbo.threading.runnables.teleport;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleport;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleportTile;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredFreezeUtil;

class TeleportActionThree implements Runnable {
    private final HabboItem currentTeleport;
    private final Room room;
    private final Habbo habbo;

    public TeleportActionThree(HabboItem currentTeleport, Room room, GameClient client) {
        this(currentTeleport, room, client == null ? null : client.getHabbo());
    }

    public TeleportActionThree(HabboItem currentTeleport, Room room, Habbo habbo) {
        this.currentTeleport = currentTeleport;
        this.habbo = habbo;
        this.room = room;
    }

    /** The entry checks the room door applies, for a teleport: a ban, and a full room. */
    static boolean mayEnter(Habbo habbo, Room room) {
        boolean anyRoom =
                habbo.hasPermission(Permission.ACC_ANYROOMOWNER) || habbo.hasPermission(Permission.ACC_ENTERANYROOM);

        if (room.isBanned(habbo) && !anyRoom) {
            return false;
        }

        return room.getUserCount() < room.getUsersMax()
                || habbo.hasPermission(Permission.ACC_FULLROOMS)
                || room.hasRights(habbo);
    }

    /** Closes the teleport and leaves the user where they are. */
    private void abort() {
        Emulator.getThreading().run(new TeleportActionFive(this.currentTeleport, this.room, this.habbo), 0);
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

        HabboItem targetTeleport;
        Room targetRoom = this.room;

        if (this.currentTeleport.getRoomId() != ((InteractionTeleport) this.currentTeleport).getTargetRoomId()) {
            targetRoom = Emulator.getGameEnvironment()
                    .getRoomManager()
                    .loadRoom(((InteractionTeleport) this.currentTeleport).getTargetRoomId());
        }

        if (targetRoom == null) {
            this.abort();
            return;
        }

        if (targetRoom.isPreLoaded()) {
            targetRoom.loadData();
        }

        targetTeleport = targetRoom.getHabboItem(((InteractionTeleport) this.currentTeleport).getTargetId());

        // Leaving for another room happens below before the entry is decided: refused there (banned,
        // or the room is full), the user used to end up in neither room while the server counted them
        // in the target one. The teleport closes and they stay where they are instead.
        if (targetRoom != this.room && !mayEnter(this.habbo, targetRoom)) {
            this.abort();
            return;
        }

        if (targetTeleport == null) {
            this.abort();
            return;
        }

        RoomTile teleportLocation = targetRoom.getLayout().getTile(targetTeleport.getX(), targetTeleport.getY());

        if (teleportLocation == null) {
            this.abort();
            return;
        }
        this.habbo.getRoomUnit().setLocation(teleportLocation);
        this.habbo.getRoomUnit().getPath().clear();
        this.habbo.getRoomUnit().removeStatus(RoomUnitStatus.MOVE);
        this.habbo.getRoomUnit().setZ(teleportLocation.getStackHeight());
        this.habbo.getRoomUnit().setPreviousLocationZ(teleportLocation.getStackHeight());

        if (targetRoom != this.room) {
            this.room.removeHabbo(this.habbo, false);
            Emulator.getGameEnvironment()
                    .getRoomManager()
                    .enterRoom(
                            this.habbo,
                            targetRoom.getId(),
                            "",
                            Emulator.getConfig().getBoolean("hotel.teleport.locked.allowed"),
                            teleportLocation);
        }

        this.habbo.getRoomUnit().setRotation(RoomUserRotation.values()[targetTeleport.getRotation() % 8]);
        this.habbo.getRoomUnit().statusUpdate(true);

        targetTeleport.setExtradata("2");
        targetRoom.updateItem(targetTeleport);
        // targetRoom.updateHabbo(this.habbo);
        // LOGGER.info((targetTeleport.getX() + " | " + targetTeleport.getY());
        this.habbo.getHabboInfo().setCurrentRoom(targetRoom);
        com.eu.habbo.habbohotel.quests.QuestProgressEvents.progress(
                this.habbo, com.eu.habbo.habbohotel.quests.QuestGoalType.USE_TELEPORT, 1);
        // Emulator.getThreading().run(new HabboItemNewState(this.currentTeleport, this.room, "0"), 500);
        Emulator.getThreading()
                .run(
                        new TeleportActionFour(targetTeleport, targetRoom, this.habbo),
                        this.currentTeleport instanceof InteractionTeleportTile ? 0 : 500);
    }
}
