package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.threading.runnables.hopper.HopperActionOne;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionHopper extends HabboItem {
    public InteractionHopper(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionHopper(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        if (room != null) {
            RoomTile loc = HabboItem.getSquareInFront(room.getLayout(), this);
            if (loc != null) {
                if (this.canUseTeleport(client, loc, room)) {
                    client.getHabbo().getRoomUnit().isTeleporting = true;
                    this.setExtradata("1");
                    room.updateItemState(this);

                    Emulator.getThreading().run(new HopperActionOne(this, room, client), 500);
                } else {
                    client.getHabbo().getRoomUnit().setGoalLocation(loc);
                }
            }
        }
    }

    @Override
    public void onPickUp(Room room) {
        this.setExtradata("0");
    }

    @Override
    public void run() {
        if (!this.getExtradata().equals("0")) {
            this.setExtradata("0");

            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
            if (room != null) {
                room.updateItemState(this);
            }
        }
        super.run();
    }

    protected boolean canUseTeleport(GameClient client, RoomTile front, Room room) {
        if (client.getHabbo().getRoomUnit().getX() != front.x)
            return false;

        if (client.getHabbo().getRoomUnit().getY() != front.y)
            return false;

        if (client.getHabbo().getRoomUnit().isTeleporting)
            return false;

        if (!room.getHabbosAt(this.getX(), this.getY()).isEmpty())
            return false;

        return this.getExtradata().equals("0");
    }
}
