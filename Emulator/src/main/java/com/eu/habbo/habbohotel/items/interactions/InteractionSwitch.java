package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.threading.runnables.RoomUnitWalkToLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class InteractionSwitch extends InteractionDefault {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionSwitch.class);

    public InteractionSwitch(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionSwitch(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canToggle(Habbo habbo, Room room) {
        return RoomLayout.tilesAdjecent(room.getLayout().getTile(this.getX(), this.getY()), habbo.getRoomUnit().getCurrentLocation());
    }

    @Override
    public boolean allowWiredResetState() {
        return true;
    }

    @Override
    public boolean isUsable() {
        return true;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client == null)
            return;

        if (!this.canToggle(client.getHabbo(), room)) {
            RoomTile closestTile = null;
            for (RoomTile tile : room.getLayout().getTilesAround(room.getLayout().getTile(this.getX(), this.getY()))) {
                if (tile.isWalkable() && (closestTile == null || closestTile.distance(client.getHabbo().getRoomUnit().getCurrentLocation()) > tile.distance(client.getHabbo().getRoomUnit().getCurrentLocation()))) {
                    closestTile = tile;
                }
            }

            if (closestTile != null && !closestTile.equals(client.getHabbo().getRoomUnit().getCurrentLocation())) {
                List<Runnable> onSuccess = new ArrayList<>();
                onSuccess.add(() -> {
                    try {
                        this.onClick(client, room, objects);
                    } catch (Exception e) {
                        LOGGER.error("Caught exception", e);
                    }
                });

                client.getHabbo().getRoomUnit().setGoalLocation(closestTile);
                Emulator.getThreading().run(new RoomUnitWalkToLocation(client.getHabbo().getRoomUnit(), closestTile, room, onSuccess, new ArrayList<>()));
            }
        }

        super.onClick(client, room, objects);
    }
}
