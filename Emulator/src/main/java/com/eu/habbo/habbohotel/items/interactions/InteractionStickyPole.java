package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionStickyPole extends InteractionDefault {
    public InteractionStickyPole(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionStickyPole(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        if (client != null && client.getHabbo() != null) {
            client.getHabbo().whisper(Emulator.getTexts().getValue("stickypole.click.info", "Place a sticky note from your inventory on a wall to leave a message!"));
        }
    }
}
