package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.outgoing.navigator.OpenRoomCreationWindowComposer;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionRoomOMatic extends InteractionDefault {
    public InteractionRoomOMatic(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionRoomOMatic(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null) {
            client.sendResponse(new OpenRoomCreationWindowComposer());
        }
    }
}