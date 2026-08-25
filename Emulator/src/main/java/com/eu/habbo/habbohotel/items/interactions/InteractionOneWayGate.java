package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.items.ItemIntStateComposer;
import com.eu.habbo.threading.runnables.RoomUnitWalkToLocation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class InteractionOneWayGate extends HabboItem {

    private boolean walkable = false;

    public InteractionOneWayGate(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionOneWayGate(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return this.getBaseItem().allowWalk();
    }

    @Override
    public boolean isWalkable() {
        return walkable;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        if (this.getExtradata().length() == 0) {
            this.setExtradata("0");
            this.needsUpdate(true);
        }

        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public void onClick(final GameClient client, final Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        if (client != null) {
            RoomTile tileInfront = room.getLayout().getTileInFront(room.getLayout().getTile(this.getX(), this.getY()), this.getRotation());
            if (tileInfront == null)
                return;

            RoomTile currentLocation = room.getLayout().getTile(this.getX(), this.getY());
            if (currentLocation == null)
                return;

            RoomUnit unit = client.getHabbo().getRoomUnit();
            if (unit == null)
                return;

            if (tileInfront.x == unit.getX() && tileInfront.y == unit.getY()) {
                if (!currentLocation.hasUnits()) {
                    List<Runnable> onSuccess = new ArrayList<Runnable>();
                    List<Runnable> onFail = new ArrayList<Runnable>();

                    onSuccess.add(() -> {
                        unit.setCanLeaveRoomByDoor(false);
                        walkable = this.getBaseItem().allowWalk();
                        RoomTile tile = room.getLayout().getTileInFront(room.getLayout().getTile(this.getX(), this.getY()), this.getRotation() + 4);
                        unit.setGoalLocation(tile);
                        Emulator.getThreading().run(new RoomUnitWalkToLocation(unit, tile, room, onFail, onFail));

                        Emulator.getThreading().run(() -> {
                            if (room.isLoaded()) {
                                WiredManager.triggerUserWalksOn(room, unit, this);
                            }
                        }, 500);
                    });

                    onFail.add(() -> {
                        unit.setCanLeaveRoomByDoor(true);
                        walkable = this.getBaseItem().allowWalk();
                        room.updateTile(currentLocation);
                        room.sendComposer(new ItemIntStateComposer(this.getId(), 0).compose());
                        unit.removeOverrideTile(currentLocation);
                    });

                    walkable = true;
                    room.updateTile(currentLocation);
                    unit.addOverrideTile(currentLocation);
                    unit.setGoalLocation(currentLocation);
                    Emulator.getThreading().run(new RoomUnitWalkToLocation(unit, currentLocation, room, onSuccess, onFail));
                    room.sendComposer(new ItemIntStateComposer(this.getId(), 1).compose());

                    /*
                    room.scheduledTasks.add(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            gate.roomUnitID = client.getHabbo().getRoomUnit().getId();
                            room.updateTile(gatePosition);
                            client.getHabbo().getRoomUnit().setGoalLocation(room.getLayout().getTileInFront(room.getLayout().getTile(InteractionOneWayGate.this.getX(), InteractionOneWayGate.this.getY()), InteractionOneWayGate.this.getRotation() + 4));
                        }
                    });
                    */
                }
            }
        }
    }

    private void refresh(Room room) {
        this.setExtradata("0");
        room.sendComposer(new ItemIntStateComposer(this.getId(), 0).compose());
        room.updateTile(room.getLayout().getTile(this.getX(), this.getY()));
    }

    @Override
    public void onPickUp(Room room) {
        this.setExtradata("0");
        this.refresh(room);
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);
        this.refresh(room);
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);
        this.refresh(room);
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        super.onMove(room, oldLocation, newLocation);
        this.refresh(room);
    }
}
