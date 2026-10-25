package com.eu.habbo.threading.runnables.teleport;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleport;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleportTile;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredFreezeUtil;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.threading.runnables.HabboItemNewState;

class TeleportActionTwo implements Runnable {
    private final HabboItem currentTeleport;
    private final Room room;
    private final Habbo habbo;

    public TeleportActionTwo(HabboItem currentTeleport, Room room, GameClient client) {
        this(currentTeleport, room, client == null ? null : client.getHabbo());
    }

    public TeleportActionTwo(HabboItem currentTeleport, Room room, Habbo habbo) {
        this.currentTeleport = currentTeleport;
        this.habbo = habbo;
        this.room = room;
    }

    @Override
    public void run() {
        TeleportChain.run(this.habbo, this.currentTeleport, this.room, this::step);
    }

    private void step() {
        int delayOffset = 500;

        if (this.currentTeleport instanceof InteractionTeleportTile) {
            delayOffset = 0;
        }

        if (this.habbo.getHabboInfo().getCurrentRoom() != this.room) {
            this.habbo.getHabboInfo().setLoadingRoom(0);
            this.habbo.getRoomUnit().isTeleporting = false;
            WiredFreezeUtil.restoreWalkState(this.habbo.getRoomUnit());
            return;
        }

        this.habbo.getRoomUnit().removeStatus(RoomUnitStatus.MOVE);
        this.room.sendComposer(new RoomUserStatusComposer(this.habbo.getRoomUnit()).compose());

        if (((InteractionTeleport) this.currentTeleport).getTargetRoomId() > 0
                && ((InteractionTeleport) this.currentTeleport).getTargetId() > 0) {
            HabboItem item = this.room.getHabboItem(((InteractionTeleport) this.currentTeleport).getTargetId());
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
            // Emulator.getThreading().run(new HabboItemNewState(this.currentTeleport, room, "1"), 0);
            Emulator.getThreading().run(new TeleportActionFive(this.currentTeleport, this.room, this.habbo), 0);
            return;
        }

        Emulator.getThreading().run(new HabboItemNewState(this.currentTeleport, this.room, "2"), delayOffset);
        Emulator.getThreading().run(new HabboItemNewState(this.currentTeleport, this.room, "0"), delayOffset + 1000);
        Emulator.getThreading().run(new TeleportActionThree(this.currentTeleport, this.room, this.habbo), delayOffset);
    }
}
