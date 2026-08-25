package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class InteractionRoomAds extends InteractionCustomValues {
    public final static Map<String, String> defaultValues = new HashMap<String, String>() {
        {
            this.put("imageUrl", "");
        }

        {
            this.put("clickUrl", "");
        }

        {
            this.put("offsetX", "0");
        }

        {
            this.put("offsetY", "0");
        }

        {
            this.put("offsetZ", "0");
        }

        {
            this.put("scale", "100");
        }
    };

    public InteractionRoomAds(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, defaultValues);
    }

    public InteractionRoomAds(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
}
