package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.WiredEffectType;
import polaris.threading.runnables.RoomUnitWalkToLocation;

public class InteractionFireworks extends InteractionDefault {

    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionFireworks.class);

    private static final String STATE_CHARGED = "1";
    private static final String STATE_EXPLOSION = "2";

    public InteractionFireworks(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionFireworks(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    /**
     * Checked in Habbo on 2021-01-03
     * - Fireworks should be charged to be able to detonate them
     * - Habbos with Rights can detonate fireworks from anywhere in a room
     * - Habbos without rights have to walk to an adjecent tile to be able to detonate (see Interaction Switch)
     * - Wired can always detonate fireworks
     */
    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (room == null) return;

        // Wireds can always detonate fireworks if charged
        if (objects.length >= 2
                && objects[1] instanceof WiredEffectType
                && objects[1] == WiredEffectType.TOGGLE_STATE) {
            if (this.getExtradata().equalsIgnoreCase(STATE_CHARGED)) {
                super.onClick(client, room, objects);

                if (this.getExtradata().equalsIgnoreCase(STATE_EXPLOSION)) {
                    this.reCharge(room);
                }
            }

            return;
        }

        if (client == null) return;

        // Habbos without rights have to walk to an adjecent tile to be able to detonate the fireworks
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

        if (this.getExtradata().equalsIgnoreCase(STATE_CHARGED)) {
            super.onClick(client, room, objects);

            if (this.getExtradata().equalsIgnoreCase(STATE_EXPLOSION)) {
                this.reCharge(room);
                AchievementManager.progressAchievement(
                        client.getUser(),
                        Emulator.getGameEnvironment().getAchievementManager().getAchievement("FireworksCharger"));
            }
        }
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);
        this.setExtradata(STATE_CHARGED);
    }

    @Override
    public boolean canToggle(User user, Room room) {
        return room.hasRights(user)
                || RoomLayout.tilesAdjecent(
                        room.getLayout().getTile(this.getX(), this.getY()),
                        user.getRoomUnit().getCurrentLocation());
    }

    private void reCharge(Room room) {
        // Default = 5000, Nuclear Firework should have 10000 in its custom params according to Habbo
        int explodeDuration = 5000;
        if (!this.getBaseItem().getCustomParams().isEmpty()) {
            try {
                explodeDuration = Integer.parseInt(this.getBaseItem().getCustomParams());
            } catch (NumberFormatException e) {
                LOGGER.error(
                        "Incorrect customparams ({}) for base item ID ({}) of type ({})",
                        this.getBaseItem().getCustomParams(),
                        this.getBaseItem().getId(),
                        this.getBaseItem().getName());
            }
        }

        Emulator.getThreading()
                .run(
                        () -> {
                            this.setExtradata(STATE_CHARGED);
                            this.needsUpdate(true);
                            room.updateItemState(this);
                        },
                        explodeDuration);
    }
}
