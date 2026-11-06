package polaris.threading.runnables.teleport;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredFreezeUtil;

class TeleportActionFour implements Runnable {
    private final UserItem currentTeleport;
    private final Room room;
    private final User user;

    public TeleportActionFour(UserItem currentTeleport, Room room, GameClient client) {
        this(currentTeleport, room, client == null ? null : client.getUser());
    }

    public TeleportActionFour(UserItem currentTeleport, Room room, User user) {
        this.currentTeleport = currentTeleport;
        this.user = user;
        this.room = room;
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
            this.currentTeleport.setExtradata("0");
            this.room.updateItem(this.currentTeleport);
            return;
        }

        if (this.user.getRoomUnit() != null) {
            this.user.getRoomUnit().isLeavingTeleporter = true;
        }

        Emulator.getThreading().run(new TeleportActionFive(this.currentTeleport, this.room, this.user), 500);
    }
}
