package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.threading.runnables.RoomUnitWalkToLocation;
import com.eu.habbo.threading.runnables.teleport.TeleportActionOne;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class InteractionTeleport extends HabboItem {
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

        Habbo habbo = client.getHabbo();

        if (habbo == null)
            return;

        RoomUnit unit = habbo.getRoomUnit();

        if (unit == null)
            return;

        RoomTile currentLocation = room.getLayout().getTile(this.getX(), this.getY());

        if (currentLocation == null)
            return;

        RoomTile infrontTile = room.getLayout().getTileInFront(currentLocation, this.getRotation());

        if (!canUseTeleport(client, room))
            return;

        if (this.roomUnitID == unit.getId() && unit.getCurrentLocation().equals(currentLocation)) {
            startTeleport(room, habbo);
            walkable = true;

            try {
                super.onClick(client, room, new Object[]{"TOGGLE_OVERRIDE"});
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        } else if (unit.getCurrentLocation().equals(currentLocation) || unit.getCurrentLocation().equals(infrontTile)) {
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
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
    }

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

        Habbo habbo = client.getHabbo();

        if (habbo == null)
            return false;

        RoomUnit unit = habbo.getRoomUnit();

        if (unit == null)
            return false;

        return habbo.getHabboInfo().getRiding() == null;
    }

    public void startTeleport(Room room, Habbo habbo) {
        this.startTeleport(room, habbo, 500);
    }

    public void startTeleport(Room room, Habbo habbo, int delay) {
        if (habbo.getRoomUnit().isTeleporting) {
            walkable = this.getBaseItem().allowWalk();
            return;
        }

        this.roomUnitID = -1;
        habbo.getRoomUnit().isTeleporting = true;
        Emulator.getThreading().run(new TeleportActionOne(this, room, habbo.getClient()), delay);
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
