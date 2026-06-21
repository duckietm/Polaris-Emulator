package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class InteractionTileEffectProvider extends InteractionCustomValues {
    public static Map<String, String> defaultValues = new HashMap<String, String>() {
        {
            this.put("effectId", "0");
        }
    };

    public InteractionTileEffectProvider(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, defaultValues);
    }

    public InteractionTileEffectProvider(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells, defaultValues);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, final Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        int effectId = Integer.parseInt(this.values.get("effectId"));

        if (roomUnit.getEffectId() == effectId) {
            effectId = 0;
        }

        this.values.put("state", "1");
        room.updateItem(this);

        final InteractionTileEffectProvider proxy = this;
        Emulator.getThreading().run(() -> {
            proxy.values.put("state", "0");
            room.updateItem(proxy);
        }, 500);

        room.giveEffect(roomUnit, effectId, -1);
    }
}
