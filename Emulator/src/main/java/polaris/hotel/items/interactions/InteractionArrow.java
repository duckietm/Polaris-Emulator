package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.rooms.RoomUserRotation;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.threading.runnables.UserItemNewState;
import polaris.threading.runnables.teleport.TeleportEntryRules;

/**
 * The public-room arrow ({@code room_wl15_teleblock}, interaction type "arrow"): a pair of linked tiles, like a
 * teleport pair, without a door. A user whose walk ends on one arrow is put on the other one, facing its rotation:
 * in the same room at once, in another room by entering that room on it. Walking over an arrow on the way somewhere
 * else does nothing, and neither does an arrow without a partner.
 */
public class InteractionArrow extends InteractionTeleport {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionArrow.class);

    /** The user's step onto the arrow finishes on the client before they are moved. */
    static final int TRAVEL_DELAY_MS = 500;

    /** The sparkle the arrow graphic plays in its state 2, shown on both arrows while a user goes through. */
    static final String SPARKLE_STATE = "2";

    static final String IDLE_STATE = "0";

    /** About one run of the sparkle animation (11 frames, each shown twice). */
    static final int SPARKLE_MS = 1000;

    public InteractionArrow(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionArrow(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    /** An arrow has no door to open: clicking it does nothing. */
    @Override
    public void onClick(GameClient client, Room room, Object[] objects) {}

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        if (room == null || roomUnit == null || !isWalkStep(objects) || !this.endsWalkHere(roomUnit)) return;

        User user = room.getUser(roomUnit);

        if (user == null || user.getClient() == null || !this.canUseTeleport(user.getClient(), room)) return;

        roomUnit.isTeleporting = true;
        Emulator.getThreading().run(() -> this.travel(room, user), TRAVEL_DELAY_MS);
    }

    /**
     * Whether the walk-on comes from a step of a walk: the walk passes the tile left and the tile stepped onto. A user
     * placed on the arrow (entering a room on it, a height update) gets a walk-on without them, and an arrow taken on
     * it would send the user straight back through its partner, back and forth.
     */
    static boolean isWalkStep(Object[] objects) {
        return objects != null
                && objects.length >= 2
                && objects[0] instanceof RoomTile
                && objects[1] instanceof RoomTile;
    }

    /** Whether this step is the end of the unit's walk (its goal is the arrow), and no teleport runs for it yet. */
    boolean endsWalkHere(RoomUnit unit) {
        return walkEndsOn(unit, this);
    }

    /** Whether the unit's walk ends on the item's tile (its goal), and no teleport runs for it yet. */
    static boolean walkEndsOn(RoomUnit unit, UserItem item) {
        RoomTile goal = unit.getGoal();

        return !unit.isTeleporting && goal != null && goal.x == item.getX() && goal.y == item.getY();
    }

    private void travel(Room room, User user) {
        RoomUnit unit = user.getRoomUnit();

        try {
            if (unit == null
                    || user.getUserInfo().getCurrentRoom() != room
                    || unit.getX() != this.getX()
                    || unit.getY() != this.getY()) {
                return;
            }

            int[] target = Emulator.getGameEnvironment().getItemManager().getTargetTeleportRoomId(this);

            if (target.length != 2) return;

            if (target[0] == room.getId()) {
                UserItem partner = room.getUserItem(target[1]);

                if (partner == null) return;

                sparkle(room, this);
                sparkle(room, partner);
                placeOn(room, unit, partner);
                return;
            }

            this.travelToRoom(room, user, target[0], target[1]);
        } catch (RuntimeException e) {
            LOGGER.error(
                    "Arrow {} could not move user {}",
                    this.getId(),
                    user.getUserInfo().getId(),
                    e);
        } finally {
            if (unit != null) unit.isTeleporting = false;
        }
    }

    private void travelToRoom(Room room, User user, int targetRoomId, int targetItemId) {
        Room targetRoom = Emulator.getGameEnvironment().getRoomManager().loadRoom(targetRoomId);

        if (targetRoom == null) return;

        if (targetRoom.isPreLoaded()) targetRoom.loadData();

        UserItem partner = targetRoom.getUserItem(targetItemId);

        if (partner == null || targetRoom.getLayout() == null || !TeleportEntryRules.mayEnter(user, targetRoom)) {
            return;
        }

        RoomTile tile = targetRoom.getLayout().getTile(partner.getX(), partner.getY());

        if (tile == null) return;

        RoomUnit unit = user.getRoomUnit();
        unit.setLocation(tile);
        unit.getPath().clear();
        unit.removeStatus(RoomUnitStatus.MOVE);
        unit.setZ(standingHeight(partner));
        unit.setPreviousLocationZ(unit.getZ());
        unit.setRotation(rotationOf(partner));

        sparkle(room, this);
        sparkle(targetRoom, partner);
        room.removeUser(user, false);
        Emulator.getGameEnvironment()
                .getRoomManager()
                .enterRoom(
                        user,
                        targetRoom.getId(),
                        "",
                        Emulator.getConfig().getBoolean("hotel.teleport.locked.allowed"),
                        tile);
        polaris.hotel.quests.QuestProgressEvents.progress(user, polaris.hotel.quests.QuestGoalType.USE_TELEPORT, 1);
    }

    /**
     * A user entering a room on an arrow stands on it and may walk at once. A furniture teleport arrives marked as
     * teleporting, without the door, until its door walks it out; an arrow has no door, so nothing else clears it and
     * the user could not walk in the new room.
     */
    public static void arrive(RoomUnit unit, InteractionArrow arrow) {
        unit.isTeleporting = false;
        unit.setCanLeaveRoomByDoor(true);
        unit.setZ(standingHeight(arrow));
        unit.setPreviousLocationZ(unit.getZ());
        unit.setRotation(rotationOf(arrow));
    }

    /** Plays the arrow's sparkle (state 2) for about a second, then puts it back to idle. */
    static void sparkle(Room room, UserItem arrow) {
        startSparkle(room, arrow);
        Emulator.getThreading().run(new UserItemNewState(arrow, room, IDLE_STATE), SPARKLE_MS);
    }

    /** Puts the arrow in its sparkle state and tells the room. */
    static void startSparkle(Room room, UserItem arrow) {
        new UserItemNewState(arrow, room, SPARKLE_STATE).run();
    }

    /** Puts the unit on the partner arrow in the same room, facing the way the arrow points. */
    static void placeOn(Room room, RoomUnit unit, UserItem partner) {
        RoomTile tile = room.getLayout() == null ? null : room.getLayout().getTile(partner.getX(), partner.getY());

        if (tile == null) return;

        unit.setLocation(tile);
        unit.getPath().clear();
        unit.removeStatus(RoomUnitStatus.MOVE);
        unit.setZ(standingHeight(partner));
        unit.setPreviousLocationZ(unit.getZ());
        unit.setRotation(rotationOf(partner));
        unit.statusUpdate(true);
    }

    /**
     * On top of the arrow. The tile's stack height is not used: nobody may stack on an arrow, and a tile without
     * stacking gives the floor height.
     */
    static double standingHeight(UserItem arrow) {
        return arrow.getZ() + Item.getCurrentHeight(arrow);
    }

    /**
     * The way a user arriving on an arrow faces: the way the arrow points. The arrow graphic points opposite to the
     * furni's rotation, so that is half a turn from it.
     */
    static RoomUserRotation rotationOf(UserItem arrow) {
        return RoomUserRotation.values()[Math.floorMod(arrow.getRotation() + 4, 8)];
    }
}
