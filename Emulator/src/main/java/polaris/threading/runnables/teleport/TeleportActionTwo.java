package polaris.threading.runnables.teleport;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.interactions.InteractionTeleport;
import polaris.hotel.items.interactions.InteractionTeleportTile;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredFreezeUtil;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;
import polaris.threading.runnables.UserItemNewState;

class TeleportActionTwo implements Runnable {
    private final UserItem currentTeleport;
    private final Room room;
    private final User user;

    public TeleportActionTwo(UserItem currentTeleport, Room room, GameClient client) {
        this(currentTeleport, room, client == null ? null : client.getUser());
    }

    public TeleportActionTwo(UserItem currentTeleport, Room room, User user) {
        this.currentTeleport = currentTeleport;
        this.user = user;
        this.room = room;
    }

    @Override
    public void run() {
        TeleportChain.run(this.user, this.currentTeleport, this.room, this::step);
    }

    private void step() {
        int delayOffset = 500;

        if (this.currentTeleport instanceof InteractionTeleportTile) {
            delayOffset = 0;
        }

        if (this.user.getUserInfo().getCurrentRoom() != this.room) {
            this.user.getUserInfo().setLoadingRoom(0);
            this.user.getRoomUnit().isTeleporting = false;
            WiredFreezeUtil.restoreWalkState(this.user.getRoomUnit());
            return;
        }

        this.user.getRoomUnit().removeStatus(RoomUnitStatus.MOVE);
        this.room.sendComposer(new RoomUserStatusComposer(this.user.getRoomUnit()).compose());

        if (((InteractionTeleport) this.currentTeleport).getTargetRoomId() > 0
                && ((InteractionTeleport) this.currentTeleport).getTargetId() > 0) {
            UserItem item = this.room.getUserItem(((InteractionTeleport) this.currentTeleport).getTargetId());
            if (item == null) {
                ((InteractionTeleport) this.currentTeleport).setTargetRoomId(0);
                ((InteractionTeleport) this.currentTeleport).setTargetId(0);
            } else if (((InteractionTeleport) item).getTargetRoomId()
                    != ((InteractionTeleport) this.currentTeleport).getTargetRoomId()) {
                ((InteractionTeleport) this.currentTeleport).setTargetId(0);
                ((InteractionTeleport) this.currentTeleport).setTargetRoomId(0);
                ((InteractionTeleport) item).setTargetId(0);
                ((InteractionTeleport) item).setTargetRoomId(0);
            }
        } else {
            ((InteractionTeleport) this.currentTeleport).setTargetRoomId(0);
            ((InteractionTeleport) this.currentTeleport).setTargetId(0);
        }
        if (((InteractionTeleport) this.currentTeleport).getTargetId() == 0) {
            int[] targetTeleport =
                    Emulator.getGameEnvironment().getItemManager().getTargetTeleportRoomId(this.currentTeleport);

            if (targetTeleport.length == 2) {
                ((InteractionTeleport) this.currentTeleport).setTargetRoomId(targetTeleport[0]);
                ((InteractionTeleport) this.currentTeleport).setTargetId(targetTeleport[1]);
            }
        }

        this.currentTeleport.setExtradata("0");
        this.room.updateItem(this.currentTeleport);

        if (((InteractionTeleport) this.currentTeleport).getTargetRoomId() == 0) {
            // Emulator.getThreading().run(new UserItemNewState(this.currentTeleport, room, "1"), 0);
            Emulator.getThreading().run(new TeleportActionFive(this.currentTeleport, this.room, this.user), 0);
            return;
        }

        Emulator.getThreading().run(new UserItemNewState(this.currentTeleport, this.room, "2"), delayOffset);
        Emulator.getThreading().run(new UserItemNewState(this.currentTeleport, this.room, "0"), delayOffset + 1000);
        Emulator.getThreading().run(new TeleportActionThree(this.currentTeleport, this.room, this.user), delayOffset);
    }
}
