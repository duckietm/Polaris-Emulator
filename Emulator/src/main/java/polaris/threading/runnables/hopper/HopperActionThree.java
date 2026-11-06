package polaris.threading.runnables.hopper;

import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.interactions.InteractionCostumeHopper;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.rooms.RoomUserRotation;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;
import polaris.threading.runnables.UserItemNewState;

class HopperActionThree implements Runnable {
    private final UserItem teleportOne;
    private final Room room;
    private final GameClient client;
    private final int targetRoomId;
    private final int targetItemId;

    public HopperActionThree(UserItem teleportOne, Room room, GameClient client, int targetRoomId, int targetItemId) {
        this.teleportOne = teleportOne;
        this.room = room;
        this.client = client;
        this.targetRoomId = targetRoomId;
        this.targetItemId = targetItemId;
    }

    @Override
    public void run() {
        UserItem targetTeleport;
        Room targetRoom = this.room;

        if (this.teleportOne.getRoomId() != this.targetRoomId) {
            Emulator.getGameEnvironment().getRoomManager().leaveRoom(this.client.getUser(), this.room, false);
            targetRoom = Emulator.getGameEnvironment().getRoomManager().loadRoom(this.targetRoomId);
            Emulator.getGameEnvironment()
                    .getRoomManager()
                    .enterRoom(this.client.getUser(), targetRoom.getId(), "", false);
        }

        targetTeleport = targetRoom.getUserItem(this.targetItemId);

        if (targetTeleport == null) {
            this.client.getUser().getRoomUnit().removeStatus(RoomUnitStatus.MOVE);
            this.client.getUser().getRoomUnit().isTeleporting = false;
            this.client.getUser().getRoomUnit().setCanWalk(true);
            return;
        }

        targetTeleport.setExtradata("2");
        targetRoom.updateItem(targetTeleport);
        this.client
                .getUser()
                .getRoomUnit()
                .setLocation(this.room.getLayout().getTile(targetTeleport.getX(), targetTeleport.getY()));
        this.client.getUser().getRoomUnit().setPreviousLocationZ(targetTeleport.getZ());
        this.client.getUser().getRoomUnit().setZ(targetTeleport.getZ());
        this.client.getUser().getRoomUnit().setRotation(RoomUserRotation.values()[targetTeleport.getRotation() % 8]);
        this.client.getUser().getRoomUnit().removeStatus(RoomUnitStatus.MOVE);
        targetRoom.sendComposer(new RoomUserStatusComposer(this.client.getUser().getRoomUnit()).compose());

        Emulator.getThreading().run(new UserItemNewState(this.teleportOne, this.room, "0"), 500);
        Emulator.getThreading().run(new HopperActionFour(targetTeleport, targetRoom, this.client), 500);

        if (targetTeleport instanceof InteractionCostumeHopper) {
            AchievementManager.progressAchievement(
                    this.client.getUser(),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("CostumeHopper"));
        }
    }
}
