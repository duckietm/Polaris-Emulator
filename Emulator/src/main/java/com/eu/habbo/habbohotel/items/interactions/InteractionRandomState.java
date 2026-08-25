package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.RandomStateParams;
import com.eu.habbo.habbohotel.rooms.Room;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionRandomState extends InteractionDefault {
    public InteractionRandomState(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionRandomState(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);

        this.setExtradata("");
        room.updateItemState(this);
    }

    public void onRandomStateClick(GameClient client, Room room) throws Exception {
        RandomStateParams params = new RandomStateParams(this.getBaseItem().getCustomParams());

        this.setExtradata("");
        room.updateItemState(this);

        int randomState = Emulator.getRandom().nextInt(params.getStates()) + 1;

        Emulator.getThreading().run(() -> {
            this.setExtradata(randomState + "");
            room.updateItemState(this);
        }, params.getDelay());
    }
}
