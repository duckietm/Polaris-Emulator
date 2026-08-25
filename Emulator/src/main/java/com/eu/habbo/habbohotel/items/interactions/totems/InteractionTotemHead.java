package com.eu.habbo.habbohotel.items.interactions.totems;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionTotemHead extends InteractionDefault {

    public InteractionTotemHead(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionTotemHead(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public TotemType getTotemType() {
        int extraData;
        try {
            extraData = Integer.parseInt(this.getExtradata());
        } catch(NumberFormatException ex) {
            extraData = 0;
        }
        if(extraData < 3) {
            return TotemType.fromInt(extraData + 1);
        }
        return TotemType.fromInt((int)Math.ceil((extraData - 2) / 4.0f));
    }

    public TotemColor getTotemColor() {
        int extraData;
        try {
            extraData = Integer.parseInt(this.getExtradata());
        }catch(NumberFormatException ex) {
            extraData = 0;
        }
        if(extraData < 3) {
            return TotemColor.NONE;
        }
        return TotemColor.fromInt(extraData - 3 - (4 * (getTotemType().type - 1)));
    }

    private void update(Room room, RoomTile tile) {
        InteractionTotemLegs legs = null;

        for(HabboItem item : room.getItemsAt(tile)) {
            if(item instanceof InteractionTotemLegs && item.getZ() < this.getZ())
                legs = (InteractionTotemLegs)item;
        }

        if(legs == null)
            return;

        this.setExtradata(((4 * this.getTotemType().type) + legs.getTotemColor().color) - 1 + "");
    }

    public void updateTotemState(Room room) {
        updateTotemState(room, room.getLayout().getTile(this.getX(), this.getY()));
    }

    public void updateTotemState(Room room, RoomTile tile) {
        this.setExtradata(getTotemType().type - 1 + "");
        update(room, tile);
        this.needsUpdate(true);
        room.updateItem(this);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (!((client != null && room != null && room.hasRights(client.getHabbo())) || (objects.length >= 2 && objects[1] instanceof WiredEffectType)))
            return;

        TotemType newType = TotemType.fromInt(getTotemType().type + 1);
        if(newType == TotemType.NONE) {
            newType = TotemType.TROLL;
        }

        this.setExtradata(newType.type - 1 + "");

        updateTotemState(room);
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        super.onMove(room, oldLocation, newLocation);
        updateTotemState(room, newLocation);
    }
}
