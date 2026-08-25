package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.threading.runnables.CannonKickAction;
import com.eu.habbo.threading.runnables.CannonResetCooldownAction;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class InteractionCannon extends HabboItem {
    public boolean cooldown = false;

    public InteractionCannon(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionCannon(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
        return true;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null) {
            super.onClick(client, room, objects);
        }

        if (room == null)
            return;

        RoomTile tile = room.getLayout().getTile(this.getX(), this.getY());
        RoomTile fuseTile = this.getRotation() >= 4 ? tile : room.getLayout().getTileInFront(tile, ((this.getRotation() % 2) + 2) % 8);
        List<RoomTile> tiles = room.getLayout().getTilesAround(fuseTile);
        tiles.remove(room.getLayout().getTileInFront(tile, (this.getRotation() + (this.getRotation() >= 4 ? -1 : 0)) % 8));
        tiles.remove(room.getLayout().getTileInFront(tile, (this.getRotation() + (this.getRotation() >= 4 ? 5 : 4)) % 8));

        if ((client == null || (tiles.contains(client.getHabbo().getRoomUnit().getCurrentLocation())) && client.getHabbo().getRoomUnit().canWalk()) && !this.cooldown) {
            if (client != null) {
                client.getHabbo().getRoomUnit().setCanWalk(false);
                client.getHabbo().getRoomUnit().setGoalLocation(client.getHabbo().getRoomUnit().getCurrentLocation());
                client.getHabbo().getRoomUnit().lookAtPoint(fuseTile);
                client.getHabbo().getRoomUnit().statusUpdate(true);
            }

            this.cooldown = true;
            this.setExtradata(this.getExtradata().equals("1") ? "0" : "1");
            room.updateItemState(this);
            Emulator.getThreading().run(new CannonKickAction(this, room, client), 750);
            Emulator.getThreading().run(new CannonResetCooldownAction(this), 2000);
        }
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);
    }

    @Override
    public void onPickUp(Room room) {
        this.setExtradata("0");
    }


    @Override
    public boolean isUsable() {
        return true;
    }
}
