package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionSwitchRemoteControl extends InteractionDefault {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionSwitchRemoteControl.class);

    public InteractionSwitchRemoteControl(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionSwitchRemoteControl(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }


    @Override
    public boolean isUsable() {
        return true;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (room != null) {
            super.onClick(client, room, objects);

            if (this.getExtradata().isEmpty())
                this.setExtradata("0");

            if (this.getBaseItem().getStateCount() > 0) {
                int currentState = 0;

                try {
                    currentState = Integer.parseInt(this.getExtradata());
                } catch (NumberFormatException e) {
                    LOGGER.error("Incorrect extradata ({}) for item ID ({}) of type ({})", this.getExtradata(), this.getId(), this.getBaseItem().getName());
                }

                this.setExtradata("" + (currentState + 1) % this.getBaseItem().getStateCount());
                this.needsUpdate(true);

                room.updateItemState(this);
            }
        }
    }
}
