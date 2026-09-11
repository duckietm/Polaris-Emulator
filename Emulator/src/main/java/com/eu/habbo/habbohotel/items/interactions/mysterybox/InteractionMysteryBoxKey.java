package com.eu.habbo.habbohotel.items.interactions.mysterybox;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The key half of the mystery box. Clicking it answers a wait somebody already opened on a box of
 * the same colour: that is the moment both players get their prize and both pieces are used up.
 */
public class InteractionMysteryBoxKey extends InteractionDefault {
    public InteractionMysteryBoxKey(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionMysteryBoxKey(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client == null || room == null) return;

        Habbo habbo = client.getHabbo();

        if (habbo == null) return;

        MysteryBoxManager.getInstance().clickKey(habbo, room, this);
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }
}
