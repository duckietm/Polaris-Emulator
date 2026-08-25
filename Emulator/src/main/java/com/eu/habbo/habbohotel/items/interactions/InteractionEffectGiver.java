package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionEffectGiver extends InteractionDefault {
    public InteractionEffectGiver(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionEffectGiver(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        if (RoomLayout.tilesAdjecent(client.getHabbo().getRoomUnit().getCurrentLocation(), room.getLayout().getTile(this.getX(), this.getY())) ||
                (client.getHabbo().getRoomUnit().getCurrentLocation().x == this.getX() && client.getHabbo().getRoomUnit().getCurrentLocation().y == this.getY())) {
            this.handle(room, client.getHabbo().getRoomUnit());
        }
    }

    protected void handle(Room room, RoomUnit roomUnit) {
        if (this.getExtradata().isEmpty()) this.setExtradata("0");

        if (!this.getExtradata().equals("0")) return;

        HabboItem instance = this;
        room.giveEffect(roomUnit, this.getBaseItem().getRandomVendingItem(), -1);

        if (this.getBaseItem().getStateCount() > 1) {
            this.setExtradata("1");
            room.updateItem(this);

            Emulator.getThreading().run(() -> {
                InteractionEffectGiver.this.setExtradata("0");
                room.updateItem(instance);
            }, 500);
        }
    }
}