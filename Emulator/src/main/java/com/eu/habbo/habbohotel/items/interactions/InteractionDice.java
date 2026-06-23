package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.plugin.events.furniture.FurnitureDiceRolledEvent;
import com.eu.habbo.threading.runnables.RandomDiceNumber;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionDice extends HabboItem {
    public InteractionDice(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public InteractionDice(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
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
        super.onClick(client, room, objects);

        if (com.eu.habbo.habbohotel.rooms.RoomDiceDisableSupport.isActive(room))
            return;

        if (client != null) {
            if (RoomLayout.tilesAdjecent(room.getLayout().getTile(this.getX(), this.getY()), client.getHabbo().getRoomUnit().getCurrentLocation())) {
                if (!this.getExtradata().equalsIgnoreCase("-1")) {
                    FurnitureDiceRolledEvent event = (FurnitureDiceRolledEvent) Emulator.getPluginManager().fireEvent(new FurnitureDiceRolledEvent(this, client.getHabbo(), -1));

                    if (event.isCancelled())
                        return;

                    this.setExtradata("-1");
                    room.updateItemState(this);
                    Emulator.getThreading().run(this);

                    if (event.result > 0) {
                        Emulator.getThreading().run(new RandomDiceNumber(room, this, event.result), 1500);
                    } else {
                        Emulator.getThreading().run(new RandomDiceNumber(this, room, this.getBaseItem().getStateCount()), 1500);
                    }
                }
            }
        }
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void onPickUp(Room room) {
        this.setExtradata("0");
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }

    @Override
    public boolean isUsable() {
        return true;
    }
}
