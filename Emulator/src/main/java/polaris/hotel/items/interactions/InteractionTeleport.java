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
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.threading.runnables.RoomUnitWalkToLocation;
import polaris.threading.runnables.teleport.TeleportActionOne;

public class InteractionTeleport extends UserItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionTeleport.class);

    private int targetId;
    private int targetRoomId;
    private int roomUnitID = -1;
    private boolean walkable;

    public InteractionTeleport(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        walkable = baseItem.allowWalk();
        this.setExtradata("0");
    }

    public InteractionTeleport(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        walkable = item.allowWalk();
        this.setExtradata("0");
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return this.getBaseItem().allowWalk() || roomUnit.getId() == this.roomUnitID;
    }

    @Override
    public boolean isWalkable() {
        return walkable;
    }

    private void tryTeleport(GameClient client, Room room) {
        /*
           if user is on item, startTeleport
        else if user is on infront, set state 1 and walk on item
        else move to infront and interact
        */

        User user = client.getUser();

        if (user == null) return;

        RoomUnit unit = user.getRoomUnit();

        if (unit == null) return;

        RoomTile currentLocation = room.getLayout().getTile(this.getX(), this.getY());

        if (currentLocation == null) return;

        RoomTile infrontTile = room.getLayout().getTileInFront(currentLocation, this.getRotation());

        if (!canUseTeleport(client, room)) return;

        if (this.roomUnitID == unit.getId() && unit.getCurrentLocation().equals(currentLocation)) {
            startTeleport(room, user);
            walkable = true;

            try {
                super.onClick(client, room, new Object[] {"TOGGLE_OVERRIDE"});
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        } else if (unit.getCurrentLocation().equals(currentLocation)
                || unit.getCurrentLocation().equals(infrontTile)) {
            // set state 1 and walk on item
            this.roomUnitID = unit.getId();
            this.setExtradata("1");
            room.updateItemState(this);
            unit.setGoalLocation(infrontTile);

            List<Runnable> onSuccess = new ArrayList<Runnable>();
            List<Runnable> onFail = new ArrayList<Runnable>();

            onSuccess.add(() -> {
                room.updateTile(currentLocation);
                tryTeleport(client, room);
                unit.removeOverrideTile(currentLocation);
                unit.setCanLeaveRoomByDoor(true);
                walkable = this.getBaseItem().allowWalk();
            });

            onFail.add(() -> {
                walkable = this.getBaseItem().allowWalk();
                room.updateTile(currentLocation);
                this.setExtradata("0");
                room.updateItemState(this);
                this.roomUnitID = -1;
                unit.removeOverrideTile(currentLocation);
                unit.setCanLeaveRoomByDoor(true);
            });

            walkable = true;
            room.updateTile(currentLocation);
            unit.addOverrideTile(currentLocation);
            unit.setGoalLocation(currentLocation);
            unit.setCanLeaveRoomByDoor(false);
            Emulator.getThreading().run(new RoomUnitWalkToLocation(unit, currentLocation, room, onSuccess, onFail));
        } else {
            // walk to teleport and interact
            List<Runnable> onSuccess = new ArrayList<Runnable>();
            List<Runnable> onFail = new ArrayList<Runnable>();

            onSuccess.add(() -> {
                tryTeleport(client, room);
            });

            unit.setGoalLocation(infrontTile);
            Emulator.getThreading().run(new RoomUnitWalkToLocation(unit, infrontTile, room, onSuccess, onFail));
        }
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (room != null && client != null && objects != null && objects.length <= 1) {
            tryTeleport(client, room);
        }
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void run() {
        if (!this.getExtradata().equals("0")) {
            this.setExtradata("0");

            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
            if (room != null) {
                room.updateItem(this);
            }
        }
        super.run();
    }

    @Override
    public void onPickUp(Room room) {
        this.targetId = 0;
        this.targetRoomId = 0;
        this.roomUnitID = -1;
        this.setExtradata("0");
    }

    public int getTargetId() {
        return this.targetId;
    }

    public void setTargetId(int targetId) {
        this.targetId = targetId;
    }

    public int getTargetRoomId() {
        return this.targetRoomId;
    }

    public void setTargetRoomId(int targetRoomId) {
        this.targetRoomId = targetRoomId;
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }

    public boolean canUseTeleport(GameClient client, Room room) {

        User user = client.getUser();

        if (user == null) return false;

        RoomUnit unit = user.getRoomUnit();

        if (unit == null) return false;

        return user.getUserInfo().getRiding() == null;
    }

    public void startTeleport(Room room, User user) {
        this.startTeleport(room, user, 500);
    }

    public void startTeleport(Room room, User user, int delay) {
        if (user.getRoomUnit().isTeleporting) {
            walkable = this.getBaseItem().allowWalk();
            return;
        }

        this.roomUnitID = -1;
        user.getRoomUnit().isTeleporting = true;
        Emulator.getThreading().run(new TeleportActionOne(this, room, user.getClient()), delay);
    }

    @Override
    public boolean isUsable() {
        return true;
    }

    @Override
    public boolean invalidatesToRoomKick() {
        return true;
    }
}
