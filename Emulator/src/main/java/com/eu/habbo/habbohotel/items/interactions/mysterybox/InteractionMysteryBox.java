package com.eu.habbo.habbohotel.items.interactions.mysterybox;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The box half of the mystery box. Clicking it looks for a key of the same colour that somebody else
 * placed in the room and, if there is one, opens the wait both players see. It is never a toggle:
 * the state of the furniture means nothing here.
 */
public class InteractionMysteryBox extends InteractionDefault {
    public InteractionMysteryBox(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionMysteryBox(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client == null || room == null) return;

        Habbo habbo = client.getHabbo();

        if (habbo == null) return;

        MysteryBoxManager.getInstance().clickBox(habbo, room, this);
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }
}
