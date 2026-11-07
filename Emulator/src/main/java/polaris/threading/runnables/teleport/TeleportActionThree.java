package polaris.threading.runnables.teleport;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.interactions.InteractionTeleport;
import polaris.hotel.items.interactions.InteractionTeleportTile;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.rooms.RoomUserRotation;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredFreezeUtil;

class TeleportActionThree implements Runnable {
    private final UserItem currentTeleport;
    private final Room room;
    private final User user;

    public TeleportActionThree(UserItem currentTeleport, Room room, GameClient client) {
        this(currentTeleport, room, client == null ? null : client.getUser());
    }

    public TeleportActionThree(UserItem currentTeleport, Room room, User user) {
        this.currentTeleport = currentTeleport;
        this.user = user;
        this.room = room;
    }

    /** The entry checks the room door applies, for a teleport: a ban, and a full room. */
    static boolean mayEnter(User user, Room room) {
        return TeleportEntryRules.mayEnter(user, room);
    }

    /** Closes the teleport and leaves the user where they are. */
    private void abort() {
        Emulator.getThreading().run(new TeleportActionFive(this.currentTeleport, this.room, this.user), 0);
    }

    @Override
    public void run() {
        TeleportChain.run(this.user, this.currentTeleport, this.room, this::step);
    }

    private void step() {
        if (this.user.getUserInfo().getCurrentRoom() != this.room) {
            this.user.getUserInfo().setLoadingRoom(0);
            this.user.getRoomUnit().isTeleporting = false;
            WiredFreezeUtil.restoreWalkState(this.user.getRoomUnit());
            return;
        }

        UserItem targetTeleport;
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

        targetTeleport = targetRoom.getUserItem(((InteractionTeleport) this.currentTeleport).getTargetId());

        // Leaving for another room happens below before the entry is decided: refused there (banned,
        // or the room is full), the user used to end up in neither room while the server counted them
        // in the target one. The teleport closes and they stay where they are instead.
        if (targetRoom != this.room && !mayEnter(this.user, targetRoom)) {
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
        this.user.getRoomUnit().setLocation(teleportLocation);
        this.user.getRoomUnit().getPath().clear();
        this.user.getRoomUnit().removeStatus(RoomUnitStatus.MOVE);
        this.user.getRoomUnit().setZ(teleportLocation.getStackHeight());
        this.user.getRoomUnit().setPreviousLocationZ(teleportLocation.getStackHeight());

        if (targetRoom != this.room) {
            this.room.removeUser(this.user, false);
            Emulator.getGameEnvironment()
                    .getRoomManager()
                    .enterRoom(
                            this.user,
                            targetRoom.getId(),
                            "",
                            Emulator.getConfig().getBoolean("hotel.teleport.locked.allowed"),
                            teleportLocation);
        }

        this.user.getRoomUnit().setRotation(RoomUserRotation.values()[targetTeleport.getRotation() % 8]);
        this.user.getRoomUnit().statusUpdate(true);

        targetTeleport.setExtradata("2");
        targetRoom.updateItem(targetTeleport);
        // targetRoom.updateUser(this.user);
        // LOGGER.info((targetTeleport.getX() + " | " + targetTeleport.getY());
        this.user.getUserInfo().setCurrentRoom(targetRoom);
        polaris.hotel.quests.QuestProgressEvents.progress(
                this.user, polaris.hotel.quests.QuestGoalType.USE_TELEPORT, 1);
        // Emulator.getThreading().run(new UserItemNewState(this.currentTeleport, this.room, "0"), 500);
        Emulator.getThreading()
                .run(
                        new TeleportActionFour(targetTeleport, targetRoom, this.user),
                        this.currentTeleport instanceof InteractionTeleportTile ? 0 : 500);
    }
}
