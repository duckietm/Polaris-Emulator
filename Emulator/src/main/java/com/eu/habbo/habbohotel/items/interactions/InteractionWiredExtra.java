package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.wired.WiredExtraDataComposer;

import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class InteractionWiredExtra extends InteractionWired {
    protected InteractionWiredExtra(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected InteractionWiredExtra(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null) {
            if (room.canInspectWired(client.getHabbo())) {
                if (this.hasConfiguration()) {
                    client.sendResponse(new WiredExtraDataComposer(this, room));
                }
                this.activateBox(room);
            }
        }
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        return true;
    }

    public boolean hasConfiguration() {
        return false;
    }
}
