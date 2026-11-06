package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;
import polaris.threading.runnables.RoomUnitWalkToLocation;

public class InteractionSwitch extends InteractionDefault {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionSwitch.class);

    public InteractionSwitch(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionSwitch(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canToggle(User user, Room room) {
        return RoomLayout.tilesAdjecent(
                room.getLayout().getTile(this.getX(), this.getY()),
                user.getRoomUnit().getCurrentLocation());
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
        if (client == null) return;

        if (!this.canToggle(client.getUser(), room)) {
            RoomTile closestTile = null;
            for (RoomTile tile :
                    room.getLayout().getTilesAround(room.getLayout().getTile(this.getX(), this.getY()))) {
                if (tile.isWalkable()
                        && (closestTile == null
                                || closestTile.distance(
                                                client.getUser().getRoomUnit().getCurrentLocation())
                                        > tile.distance(
                                                client.getUser().getRoomUnit().getCurrentLocation()))) {
                    closestTile = tile;
                }
            }

            if (closestTile != null
                    && !closestTile.equals(client.getUser().getRoomUnit().getCurrentLocation())) {
                List<Runnable> onSuccess = new ArrayList<>();
                onSuccess.add(() -> {
                    try {
                        this.onClick(client, room, objects);
                    } catch (Exception e) {
                        LOGGER.error("Caught exception", e);
                    }
                });

                client.getUser().getRoomUnit().setGoalLocation(closestTile);
                Emulator.getThreading()
                        .run(new RoomUnitWalkToLocation(
                                client.getUser().getRoomUnit(), closestTile, room, onSuccess, new ArrayList<>()));
            }
        }

        super.onClick(client, room, objects);
    }
}
