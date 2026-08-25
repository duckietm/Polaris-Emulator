package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.habboway.nux.NuxAlertComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class InteractionInformationTerminal extends InteractionCustomValues {
    public static final Map<String, String> defaultValues = new HashMap<String, String>() {
        {
            this.put("internalLink", "habbopages/chat/commands");
        }
    };

    public InteractionInformationTerminal(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, defaultValues);
    }

    public InteractionInformationTerminal(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells, defaultValues);
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalk(roomUnit, room, objects);

        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo != null && this.values.containsKey("internalLink")) {
            habbo.getClient().sendResponse(new NuxAlertComposer(this.values.get("internalLink")));
        }
    }
}
